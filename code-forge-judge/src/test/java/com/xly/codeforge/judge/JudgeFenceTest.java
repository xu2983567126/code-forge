package com.xly.codeforge.judge;

import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import com.xly.codeforge.judge.sandbox.Sandbox;
import com.xly.codeforge.judge.sandbox.SandboxFactory;
import com.xly.codeforge.judge.service.JudgeService;
import com.xly.codeforge.judge.strategy.JudgeStrategyManager;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.vo.SubmissionVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 判题并发 fencing 门禁（M1）。
 *
 * <p>核心回归：确保「只有持租约且代次匹配者能写回」，杜绝并发双判覆盖。</p>
 * <ul>
 *   <li>acquireLease 返回 0（被 fencing 挡掉）：沙箱<b>不被调用</b>、judge 返回 null、不写回。</li>
 *   <li>acquireLease 返回 1（抢占成功）：走到 writeVerdict，且传参含正确的 generation / 非空 attemptId。</li>
 * </ul>
 *
 * <p>沙箱经 {@code SandboxFactory} 静态工厂创建，用 {@code mockStatic} 拦掉真实沙箱调用；
 * 其余 Feign / 策略依赖全部 mock。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "codesandbox.type=native"
})
class JudgeFenceTest {

    @Autowired
    JudgeService judgeService;

    @MockitoBean
    SubmissionFeignClient submissionFeignClient;

    @MockitoBean
    QuestionFeignClient questionFeignClient;

    @MockitoBean
    JudgeStrategyManager judgeStrategyManager;

    @Test
    void 被fencing挡掉_沙箱不调用_返回null_不写回() {
        when(submissionFeignClient.getSubmissionById(anyLong())).thenReturn(waitingSubmission(1L, 1L));
        when(submissionFeignClient.acquireLease(any(SubmissionFenceRequest.class))).thenReturn(0);

        SubmissionVO result = judgeService.judgeSubmission(1L);

        assertThat(result).isNull();
        verify(submissionFeignClient).acquireLease(any(SubmissionFenceRequest.class));
        // 关键：被挡掉后绝不该走到写回，更不该调沙箱
        verify(submissionFeignClient, never()).writeVerdict(any(SubmissionVerdictRequest.class));
        // fencing 是第一道闸门：抢不到就连题目服务都不打（避免重复分发白白打 question RPC）
        verify(questionFeignClient, never()).getQuestionById(anyLong());
    }

    @Test
    void 抢占成功_走到writeVerdict且代次与attemptId一致() {
        Submission submission = waitingSubmission(1L, 1L);
        when(submissionFeignClient.getSubmissionById(anyLong())).thenReturn(submission);
        when(submissionFeignClient.acquireLease(any(SubmissionFenceRequest.class))).thenReturn(1);
        when(submissionFeignClient.writeVerdict(any(SubmissionVerdictRequest.class))).thenReturn(1);
        when(questionFeignClient.getQuestionById(1L)).thenReturn(questionWithCases());
        when(judgeStrategyManager.doJudge(any())).thenReturn(acceptedJudgeInfo());

        Sandbox sandbox = mock(Sandbox.class);
        when(sandbox.executeCode(any())).thenReturn(cannedResponse());

        try (MockedStatic<SandboxFactory> ms = mockStatic(SandboxFactory.class)) {
            ms.when(() -> SandboxFactory.newInstance(anyString())).thenReturn(sandbox);
            SubmissionVO result = judgeService.judgeSubmission(1L);
            assertThat(result).isNotNull();
        }

        // 写回必须发生，且 generation / attemptId 与抢占时一致
        ArgumentCaptor<SubmissionFenceRequest> acquireCap =
                ArgumentCaptor.forClass(SubmissionFenceRequest.class);
        ArgumentCaptor<SubmissionVerdictRequest> verdictCap =
                ArgumentCaptor.forClass(SubmissionVerdictRequest.class);
        verify(submissionFeignClient).acquireLease(acquireCap.capture());
        verify(submissionFeignClient).writeVerdict(verdictCap.capture());

        SubmissionFenceRequest acquired = acquireCap.getValue();
        SubmissionVerdictRequest written = verdictCap.getValue();
        assertThat(written.getId()).isEqualTo(1L);
        assertThat(written.getGeneration()).isEqualTo(1L);
        assertThat(written.getAttemptId()).isEqualTo(acquired.getAttemptId());
        assertThat(written.getAttemptId()).isNotNull();
        assertThat(written.getStatus()).isEqualTo(2);
        assertThat(written.getVerdict()).isEqualTo("ACCEPTED");
    }

    private Submission waitingSubmission(long id, long generation) {
        Submission s = new Submission();
        s.setId(id);
        s.setStatus(0);
        s.setGeneration(generation);
        s.setQuestionId(1L);
        s.setLanguage("java");
        s.setCode("public class Main {}");
        return s;
    }

    private Question questionWithCases() {
        Question q = new Question();
        q.setId(1L);
        JudgeCase jc = new JudgeCase();
        jc.setInput("1 2");
        jc.setExpectedOutput("3");
        // Question.judgeCase 是 JSON 字符串，QuestionAdminVO.objToVo 会解析成 List<JudgeCase>
        q.setJudgeCase(cn.hutool.json.JSONUtil.toJsonStr(List.of(jc)));
        return q;
    }

    private JudgeInfo acceptedJudgeInfo() {
        JudgeInfo ji = new JudgeInfo();
        ji.setMessage(VerdictEnum.ACCEPTED);
        return ji;
    }

    private ExecuteCodeResponse cannedResponse() {
        ExecuteCodeResponse r = new ExecuteCodeResponse();
        // 注意：本测试中 judgeStrategyManager.doJudge 已被 mock 直接返回 acceptedJudgeInfo()，
        // 沙箱响应内容不影响 verdict 落库，这里只留事实模型的必要字段保证可编译。
        r.setCompileError(false);
        r.setSystemError(false);
        return r;
    }
}
