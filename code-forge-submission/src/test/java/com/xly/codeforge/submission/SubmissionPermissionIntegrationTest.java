package com.xly.codeforge.submission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.vo.UserVO;
import com.xly.codeforge.submission.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 提交服务的读取权限矩阵（对应 e2e 第 [8] 组的本质）。
 *
 * <p>本服务的权限模型：登录态由 {@code UserFeignClient#getLoginUser} 解析（default 方法，
 * 本地 StpUtil + 远程取用户），读取权限在 service 层判定 —— 本人或管理员，其余 40101；
 * 列表范围由 {@code applyDataScope} 收敛，请求里的 userId 不直接进 SQL。</p>
 *
 * <p>测试边界：Feign 客户端全部 mock —— sa-token/Redis 是 user 服务的职责，
 * 这里验证的是「拿到 User 之后本服务的行为」；数据层用 Testcontainers 起真 MySQL，
 * mapper / 分页 / 逻辑删除都是真实执行。</p>
 *
 * <p>MockMvc 不感知 context-path：生产由 /api/submission 前缀补全的部分这里必须省略，直接映射方法路径。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class SubmissionPermissionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withInitScript("sql/init-question-submit.sql")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SubmissionService submissionService;

    @MockitoBean
    UserFeignClient userFeignClient;

    @MockitoBean
    QuestionFeignClient questionFeignClient;

    @MockitoBean
    JudgeFeignClient judgeFeignClient;

    private User owner;
    private User stranger;
    private User admin;
    private Long ownSubmissionId;
    private Long strangerSubmissionId;

    @BeforeEach
    void setUp() {
        // 测试间数据会累积（同一容器贯穿整个类），先清场再插固定数据，保证 total 断言稳定。
        // remove 走逻辑删除，旧记录 is_delete=1 不再出现在查询里。
        submissionService.remove(Wrappers.emptyWrapper());

        owner = user(1L, "user");
        stranger = user(2L, "user");
        admin = user(9L, "admin");

        ownSubmissionId = saveSubmission(1L, "owner-source-code", "ACCEPTED");
        strangerSubmissionId = saveSubmission(2L, "stranger-source-code", "WRONG_ANSWER");

        // 详情接口会回填题目与提交者信息
        Question question = new Question();
        question.setId(1L);
        question.setTitle("A+B");
        when(questionFeignClient.getQuestionById(anyLong())).thenReturn(question);
        when(userFeignClient.getById(1L)).thenReturn(owner);
        when(userFeignClient.getById(2L)).thenReturn(stranger);
        when(userFeignClient.getUserVO(any())).thenReturn(new UserVO());
    }

    private User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setAccount("user" + id);
        return user;
    }

    private Long saveSubmission(long userId, String code, String verdict) {
        Submission submission = new Submission();
        submission.setLanguage("java");
        submission.setCode(code);
        submission.setStatus(2);
        submission.setVerdict(verdict);
        submission.setQuestionId(1L);
        submission.setUserId(userId);
        assertThat(submissionService.save(submission)).isTrue();
        return submission.getId();
    }

    private void loggedInAs(User user) {
        when(userFeignClient.getLoginUser(any())).thenReturn(user);
        when(userFeignClient.isAdmin(user)).thenReturn("admin".equals(user.getRole()));
    }

    private void anonymous() {
        when(userFeignClient.getLoginUser(any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
    }

    @Test
    void 匿名读提交详情被拒40100() throws Exception {
        anonymous();

        mockMvc.perform(get("/" + ownSubmissionId + "/vo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void 本人读自己的详情返回源码() throws Exception {
        loggedInAs(owner);

        mockMvc.perform(get("/" + ownSubmissionId + "/vo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("owner-source-code"));
    }

    @Test
    void 本人读他人的详情被拒40101() throws Exception {
        loggedInAs(owner);

        mockMvc.perform(get("/" + strangerSubmissionId + "/vo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("只能查看自己的提交记录"));
    }

    @Test
    void 管理员读任何人的详情都放行() throws Exception {
        loggedInAs(admin);

        mockMvc.perform(get("/" + strangerSubmissionId + "/vo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("stranger-source-code"));
    }

    @Test
    void 列表请求带他人userId时被拒40101() throws Exception {
        loggedInAs(owner);

        mockMvc.perform(post("/list/page/vo")
                        .content("{\"current\":1,\"pageSize\":10,\"userId\":2}")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void 普通用户不传userId时列表自动收敛到自己的记录() throws Exception {
        loggedInAs(owner);

        // 不传 userId：若放任进 SQL 会返回全站记录；正确行为是强制落回本人
        mockMvc.perform(post("/list/page/vo")
                        .content("{\"current\":1,\"pageSize\":10}")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("owner-source-code"));
    }

    @Test
    void 管理员列表可指定查看他人的记录() throws Exception {
        loggedInAs(admin);

        mockMvc.perform(post("/list/page/vo")
                        .content("{\"current\":1,\"pageSize\":10,\"userId\":2}")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(2));
    }

    @Test
    void 列表页大小超过20被拒() throws Exception {
        loggedInAs(owner);

        mockMvc.perform(post("/list/page/vo")
                        .content("{\"current\":1,\"pageSize\":21}")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
