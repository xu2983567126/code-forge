package com.xly.codeforge.submission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.submission.mapper.SubmissionMapper;
import com.xly.codeforge.submission.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 判题并发 fencing 的 CAS 回归（M1）。
 *
 * <p>数据层用 Testcontainers 真 MySQL 跑五条 CAS + 两条捞取，验证「只有持租约且代次匹配者能写回」
 * 以及 reaper 回收逻辑的正确性。Feign 客户端全部 mock（本测试不验证跨服务调用，只验证 DB 契约）。</p>
 *
 * <p>reaper 的 {@code initial-delay-ms}/{@code interval-ms} 被压到极大，避免测试期间它抢先把
 * 我们构造的过期 / 卡住行复位，造成断言竞态。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "judge.reaper.initial-delay-ms=999999",
        "judge.reaper.interval-ms=999999"
})
@Testcontainers
class SubmissionFenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withInitScript("sql/init-question-submit.sql")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false");

    @Autowired
    SubmissionService submissionService;

    @Autowired
    SubmissionMapper submissionMapper;

    @MockitoBean
    UserFeignClient userFeignClient;

    @MockitoBean
    QuestionFeignClient questionFeignClient;

    @MockitoBean
    JudgeFeignClient judgeFeignClient;

    // 用"epoch / 远未来"而非"now±N秒"构造时间，避免 JVM 与 Testcontainers MySQL 容器时钟偏差
    // 导致"过去"在 MySQL 时钟里不算过去（reaper 的 SQL 用 DB 时钟 NOW() 比较）。
    private static final Date LONG_AGO = new Date(0L);
    private static final Date FAR_FUTURE = new Date(System.currentTimeMillis() + 86_400_000L);

    @BeforeEach
    void setUp() {
        // 逻辑删除清场，保证代次 / 状态断言稳定（reaper SQL 已带 is_delete=0，被清的行不会入选）
        submissionService.remove(Wrappers.emptyWrapper());
    }

    private Long saveRow(int status, Long generation, String attemptId, Date lease, Date updateTime) {
        Submission s = new Submission();
        s.setLanguage("java");
        s.setCode("code");
        s.setStatus(status);
        s.setQuestionId(1L);
        s.setUserId(1L);
        s.setGeneration(generation);
        s.setCurrentAttemptId(attemptId);
        s.setJudgingLeaseExpiresAt(lease);
        if (updateTime != null) {
            s.setUpdateTime(updateTime);
        }
        assertThat(submissionService.save(s)).isTrue();
        return s.getId();
    }

    @Test
    void acquireLease_抢占成功_置RUNNING且代次不变() {
        Long id = saveRow(0, 1L, null, null, null);
        SubmissionFenceRequest req = req(id, "a1", 1L, 60);

        assertThat(submissionService.acquireLease(req)).isEqualTo(1);

        Submission after = submissionService.getById(id);
        assertThat(after.getStatus()).isEqualTo(1);
        assertThat(after.getCurrentAttemptId()).isEqualTo("a1");
        assertThat(after.getJudgingLeaseExpiresAt()).isNotNull();
        assertThat(after.getGeneration()).isEqualTo(1L);
    }

    @Test
    void acquireLease_并发第二次返回0_不重复抢占() {
        Long id = saveRow(0, 1L, null, null, null);
        SubmissionFenceRequest req = req(id, "a1", 1L, 60);

        assertThat(submissionService.acquireLease(req)).isEqualTo(1);
        // 同一代次、已非 WAITING：第二次抢占必须失败（否则会 double-judge）
        assertThat(submissionService.acquireLease(req)).isEqualTo(0);
    }

    @Test
    void writeVerdict_代次与attempt匹配_写回终态并清租约() {
        Long id = saveRow(1, 1L, "a1", FAR_FUTURE, null);
        SubmissionVerdictRequest v = verdict(id, 1L, "a1", 2, "ACCEPTED", "{}");

        assertThat(submissionService.writeVerdict(v)).isEqualTo(1);

        Submission after = submissionService.getById(id);
        assertThat(after.getStatus()).isEqualTo(2);
        assertThat(after.getVerdict()).isEqualTo("ACCEPTED");
        assertThat(after.getCurrentAttemptId()).isNull();
        assertThat(after.getJudgingLeaseExpiresAt()).isNull();
    }

    @Test
    void writeVerdict_attempt不匹配返回0_防僵尸worker覆盖() {
        Long id = saveRow(1, 1L, "a1", FAR_FUTURE, null);
        SubmissionVerdictRequest v = verdict(id, 1L, "wrong-attempt", 2, "ACCEPTED", "{}");

        assertThat(submissionService.writeVerdict(v)).isEqualTo(0);

        Submission after = submissionService.getById(id);
        assertThat(after.getStatus()).isEqualTo(1); // 未被改动
        assertThat(after.getVerdict()).isNull();
    }

    @Test
    void writeVerdict_代次不匹配返回0_防reaper回收后复活覆盖() {
        // 模拟 reaper 已把代次 +1 并重派；旧 worker 苏醒写回必须被挡
        Long id = saveRow(1, 2L, "a1", FAR_FUTURE, null);
        SubmissionVerdictRequest v = verdict(id, 1L, "a1", 2, "ACCEPTED", "{}");

        assertThat(submissionService.writeVerdict(v)).isEqualTo(0);
    }

    @Test
    void bumpGenerationAndReset_回收过期RUNNING_复位WAITING且代次加1() {
        Long id = saveRow(1, 1L, "a1", LONG_AGO, null);

        assertThat(submissionMapper.bumpGenerationAndReset(id, 1L, 2L)).isEqualTo(1);

        Submission after = submissionService.getById(id);
        assertThat(after.getStatus()).isEqualTo(0);
        assertThat(after.getGeneration()).isEqualTo(2L);
        assertThat(after.getCurrentAttemptId()).isNull();
        assertThat(after.getJudgingLeaseExpiresAt()).isNull();
    }

    @Test
    void selectExpiredJudgingForUpdate_只捞出过期租约() {
        Long expired = saveRow(1, 1L, "a1", LONG_AGO, null);
        Long fresh = saveRow(1, 1L, "b1", FAR_FUTURE, null);

        List<Submission> list = submissionMapper.selectExpiredJudgingForUpdate(20);

        assertThat(list).extracting(Submission::getId).contains(expired).doesNotContain(fresh);
    }

    @Test
    void selectStuckWithoutLease_只捞出闲置过久的WAITING() {
        Long stuck = saveRow(0, 1L, null, null, LONG_AGO);
        Long recent = saveRow(0, 1L, null, null, null);

        List<Submission> list = submissionMapper.selectStuckWithoutLease(20, 30);

        assertThat(list).extracting(Submission::getId).contains(stuck).doesNotContain(recent);
    }

    @Test
    void resetStuckToWaiting_复位卡死WAITING且代次加1() {
        Long id = saveRow(0, 1L, null, null, LONG_AGO);

        assertThat(submissionMapper.resetStuckToWaiting(id, 1L)).isEqualTo(1);

        Submission after = submissionService.getById(id);
        assertThat(after.getStatus()).isEqualTo(0);
        assertThat(after.getGeneration()).isEqualTo(2L);
    }

    private SubmissionFenceRequest req(long id, String attemptId, long generation, int ttl) {
        SubmissionFenceRequest r = new SubmissionFenceRequest();
        r.setId(id);
        r.setAttemptId(attemptId);
        r.setGeneration(generation);
        r.setTtlSeconds(ttl);
        return r;
    }

    private SubmissionVerdictRequest verdict(long id, long generation, String attemptId,
                                             int status, String v, String judgeInfo) {
        SubmissionVerdictRequest r = new SubmissionVerdictRequest();
        r.setId(id);
        r.setGeneration(generation);
        r.setAttemptId(attemptId);
        r.setStatus(status);
        r.setVerdict(v);
        r.setJudgeInfo(judgeInfo);
        return r;
    }
}
