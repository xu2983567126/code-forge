package com.xly.codeforge.judge.strategy.impl;

import com.xly.codeforge.judge.sandbox.Sandbox;
import com.xly.codeforge.judge.sandbox.SandboxFactory;
import com.xly.codeforge.judge.strategy.SpecialJudgeExecutor;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link SandboxSpecialJudgeExecutor} 单测（无 Testcontainers）。
 *
 * <p>用 {@code mockStatic(SandboxFactory)} 把沙箱替身注入，验证：
 * 退出码 0/1/2/其他/超时 → ACCEPTED/WRONG_ANSWER/PRESENTATION_ERROR/SYSTEM_ERROR，
 * 且特判请求确实把「标准答案、用户输出」放进 {@code fileArgs}（沙箱物化成文件后按路径传 argv）、
 * 「测试输入」放进 {@code inputList}。</p>
 */
class SandboxSpecialJudgeExecutorTest {

    private ExecuteCodeResponse responseWith(Integer exitCode, boolean timedOut) {
        SandboxCaseResult fact = SandboxCaseResult.builder()
                .exitCode(exitCode).timedOut(timedOut).output("").timeMs(10L).memoryKb(100L)
                .errorOutput(null).truncated(false).build();
        return ExecuteCodeResponse.builder().caseResults(java.util.List.of(fact)).build();
    }

    private VerdictEnum run(Integer exitCode, boolean timedOut) {
        Sandbox sandbox = mock(Sandbox.class);
        when(sandbox.executeCode(any(ExecuteCodeRequest.class))).thenReturn(responseWith(exitCode, timedOut));
        try (MockedStatic<SandboxFactory> ms = mockStatic(SandboxFactory.class)) {
            ms.when(() -> SandboxFactory.newInstance(any())).thenReturn(sandbox);
            SpecialJudgeExecutor executor = new SandboxSpecialJudgeExecutor();
            return executor.judge("in", "expected", "user", "spjCode", "java");
        }
    }

    @Test
    void 退出码0判Accepted() {
        assertThat(run(0, false)).isEqualTo(VerdictEnum.ACCEPTED);
    }

    @Test
    void 退出码1判WrongAnswer() {
        assertThat(run(1, false)).isEqualTo(VerdictEnum.WRONG_ANSWER);
    }

    @Test
    void 退出码2判PresentationError() {
        assertThat(run(2, false)).isEqualTo(VerdictEnum.PRESENTATION_ERROR);
    }

    @Test
    void 退出码其他判SystemError_checker故障不怪用户() {
        assertThat(run(7, false)).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void 沙箱超时时判SystemError() {
        assertThat(run(0, true)).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void 特判请求透传输入与参数() {
        Sandbox sandbox = mock(Sandbox.class);
        when(sandbox.executeCode(any(ExecuteCodeRequest.class))).thenReturn(responseWith(0, false));
        ArgumentCaptor<ExecuteCodeRequest> captor = ArgumentCaptor.forClass(ExecuteCodeRequest.class);
        try (MockedStatic<SandboxFactory> ms = mockStatic(SandboxFactory.class)) {
            ms.when(() -> SandboxFactory.newInstance(any())).thenReturn(sandbox);
            SpecialJudgeExecutor executor = new SandboxSpecialJudgeExecutor();
            executor.judge("test-input", "std-output", "user-output", "spj-src", "python");
            org.mockito.Mockito.verify(sandbox).executeCode(captor.capture());
        }
        ExecuteCodeRequest req = captor.getValue();
        assertThat(req.getCode()).isEqualTo("spj-src");
        assertThat(req.getLanguage()).isEqualTo("python");
        assertThat(req.getInputList()).containsExactly("test-input");
        assertThat(req.getFileArgs()).containsExactly("std-output", "user-output");
    }
}
