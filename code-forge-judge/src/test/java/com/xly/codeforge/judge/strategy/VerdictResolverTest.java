package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.judge.sandbox.Sandbox;
import com.xly.codeforge.judge.sandbox.SandboxFactory;
import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import com.xly.codeforge.judge.strategy.impl.SandboxSpecialJudgeExecutor;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VerdictResolver} 事实路径归约回归。
 *
 * <p>验证「先限制后输出」顺序不回归（TLE/MLE 可达），并覆盖：RE 透出 errorMessage、
 * 标准比对内部空白差异判 PRESENTATION_ERROR、浮点模式精度容差判 Accepted，
 * 以及 M4 的 SPJ 分支（compareMode=SPJ 时逐用例委派 {@link SpecialJudgeExecutor}）。</p>
 */
class VerdictResolverTest {

    private static final long TIME_LIMIT = 1000L;
    private static final long MEMORY_LIMIT = 102400L;

    private final VerdictResolver resolver = new VerdictResolver();

    /** 非 SPJ 用例用的占位执行器（SPJ 分支不会被命中，返回何值无所谓）。 */
    private static final SpecialJudgeExecutor DUMMY_EXECUTOR = (i, e, u, c, l) -> VerdictEnum.ACCEPTED;

    /** 可控制返回值的特判执行器，供 SPJ 用例断言分支。 */
    private static class StubExecutor implements SpecialJudgeExecutor {
        private final VerdictEnum result;
        StubExecutor(VerdictEnum result) {
            this.result = result;
        }
        @Override
        public VerdictEnum judge(String input, String expected, String userOutput,
                                          String spjCode, String spjLanguage) {
            return result;
        }
    }

    private JudgeContext context(List<SandboxCaseResult> facts, List<TestCaseData> cases) {
        return context(facts, cases, null);
    }

    private JudgeContext context(List<SandboxCaseResult> facts, List<TestCaseData> cases, JudgeConfig config) {
        JudgeConfig cfg = config;
        if (cfg == null) {
            cfg = new JudgeConfig();
            cfg.setTimeLimit(TIME_LIMIT);
            cfg.setMemoryLimit(MEMORY_LIMIT);
        }
        return JudgeContext.builder()
                .judgeCases(cases)
                .judgeConfig(cfg)
                .sandboxCaseResults(facts)
                .build();
    }

    private JudgeContext spjContext(List<SandboxCaseResult> facts, List<TestCaseData> cases,
                                    SpecialJudgeExecutor executor, String spjCode) {
        JudgeConfig cfg = new JudgeConfig();
        cfg.setTimeLimit(TIME_LIMIT);
        cfg.setMemoryLimit(MEMORY_LIMIT);
        cfg.setCompareMode("SPJ");
        return JudgeContext.builder()
                .judgeCases(cases)
                .judgeConfig(cfg)
                .sandboxCaseResults(facts)
                .spjCode(spjCode)
                .spjLanguage("java")
                .build();
    }

    private TestCaseData judgeCase(String output) {
        return TestCaseData.builder()
                .index(0)
                .input("1")
                .expectedOutput(output)
                .build();
    }

    private SandboxCaseResult fact(Integer exitCode, boolean timedOut, String output,
                                   long timeMs, long memoryKb, String errorOutput) {
        return SandboxCaseResult.builder()
                .exitCode(exitCode)
                .timedOut(timedOut)
                .output(output)
                .timeMs(timeMs)
                .memoryKb(memoryKb)
                .errorOutput(errorOutput)
                .truncated(false)
                .build();
    }

    @Test
    void 全部通过聚合为Accepted_耗时与内存均取峰值() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 1024L, null),
                fact(0, false, "3\n", 40L, 2048L, null));

        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase("2"), judgeCase("3"))), DUMMY_EXECUTOR);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
        // 耗时与内存均取逐用例峰值（与 UltiCode 单值语义一致，不再求和；求和会让总量随用例数放大）
        assertThat(result.getTime()).isEqualTo(40L);
        assertThat(result.getMemory()).isEqualTo(2048L);
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly(VerdictEnum.ACCEPTED, VerdictEnum.ACCEPTED);
    }

    // ===== 试运行中性态：用例无期望输出时不判对错 =====

    @Test
    void 无期望输出时走中性态_聚合结论为null() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "2\n", 30L, 1024L, null));
        // expectedOutput 为空串（用户手填用例未填期望）→ 不可判定对错，仅回显实际输出
        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase(""))), DUMMY_EXECUTOR);
        assertThat(result.getMessage()).isNull();
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly((VerdictEnum) null);
    }

    @Test
    void 混合用例_可判定用例全通过则聚合Accepted_中性用例status为null() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 1024L, null),
                fact(0, false, "3\n", 40L, 2048L, null));
        JudgeInfo result = resolver.resolve(
                context(facts, List.of(judgeCase("2"), judgeCase(""))), DUMMY_EXECUTOR);
        assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly(VerdictEnum.ACCEPTED, null);
    }

    @Test
    void 无期望输出但运行时错误仍判RuntimeError() {
        // 中性态只跳过「输出比对」，超时 / 运行错误 / 内存超限仍按事实判定
        List<SandboxCaseResult> facts = List.of(fact(1, false, "", 50L, 1024L, "boom"));
        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase(""))), DUMMY_EXECUTOR);
        assertThat(result.getMessage()).isEqualTo(VerdictEnum.RUNTIME_ERROR);
    }

    @Test
    void 沙箱超时被杀时判TLE即使输出对不上() {        List<SandboxCaseResult> facts = List.of(fact(-1, true, "任意残缺输出", 5000L, 1024L, null));

        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase("2"))), DUMMY_EXECUTOR);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void 未触发沙箱超时但超过题目时限时判TLE() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "2\n", 1200L, 1024L, null));

        assertThat(resolver.resolve(context(facts, List.of(judgeCase("2"))), DUMMY_EXECUTOR).getMessage())
                .isEqualTo(VerdictEnum.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void 退出码非零时判运行时错误且透出errorMessage() {
        List<SandboxCaseResult> facts = List.of(fact(1, false, "", 50L, 1024L, "java.lang.Exception: boom"));

        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase("2"))), DUMMY_EXECUTOR);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.RUNTIME_ERROR);
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getErrorMessage)
                .containsExactly("java.lang.Exception: boom");
    }

    @Test
    void 内存超限时判MLE() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "2\n", 30L, 200000L, null));

        assertThat(resolver.resolve(context(facts, List.of(judgeCase("2"))), DUMMY_EXECUTOR).getMessage())
                .isEqualTo(VerdictEnum.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void 输出不匹配时判WA() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "错误答案\n", 30L, 1024L, null));

        assertThat(resolver.resolve(context(facts, List.of(judgeCase("3"))), DUMMY_EXECUTOR).getMessage())
                .isEqualTo(VerdictEnum.WRONG_ANSWER);
    }

    @Test
    void 内部空白差异由标准比对判PE() {
        JudgeConfig config = new JudgeConfig();
        config.setTimeLimit(TIME_LIMIT);
        config.setMemoryLimit(MEMORY_LIMIT);
        config.setCompareMode("STANDARD");
        List<SandboxCaseResult> facts = List.of(fact(0, false, "1  2", 30L, 1024L, null));

        JudgeContext judgeContext = JudgeContext.builder()
                .judgeCases(List.of(judgeCase("1 2")))
                .judgeConfig(config)
                .sandboxCaseResults(facts)
                .build();

        assertThat(resolver.resolve(judgeContext, DUMMY_EXECUTOR).getMessage()).isEqualTo(VerdictEnum.PRESENTATION_ERROR);
    }

    @Test
    void 编译失败判CompilingError即使没有任何事实明细() {
        JudgeContext judgeContext = context(null, List.of(judgeCase("2")));
        judgeContext.setCompileError(true);

        assertThat(resolver.resolve(judgeContext, DUMMY_EXECUTOR).getMessage()).isEqualTo(VerdictEnum.COMPILE_ERROR);
    }

    @Test
    void 沙箱系统错误标记优先于其它判定() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "2\n", 30L, 1024L, null));
        JudgeContext judgeContext = context(facts, List.of(judgeCase("2")));
        judgeContext.setSystemError(true);

        assertThat(resolver.resolve(judgeContext, DUMMY_EXECUTOR).getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void 用例数与事实数不一致时判SystemError() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "2\n", 30L, 1024L, null));

        assertThat(resolver.resolve(context(facts, List.of(judgeCase("2"), judgeCase("3"))), DUMMY_EXECUTOR).getMessage())
                .isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void 截断的输出不可信直接判WA() {
        SandboxCaseResult truncated = SandboxCaseResult.builder()
                .exitCode(0).timedOut(false).output("2").truncated(true)
                .timeMs(30L).memoryKb(1024L).build();

        assertThat(resolver.resolve(context(List.of(truncated), List.of(judgeCase("2"))), DUMMY_EXECUTOR).getMessage())
                .isEqualTo(VerdictEnum.WRONG_ANSWER);
    }

    @Test
    void 浮点模式精度内误差判Accepted() {
        JudgeConfig config = new JudgeConfig();
        config.setTimeLimit(TIME_LIMIT);
        config.setMemoryLimit(MEMORY_LIMIT);
        config.setCompareMode("FLOAT");
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "0.1 0.2 0.30000000000000004", 30L, 1024L, null));

        JudgeContext judgeContext = JudgeContext.builder()
                .judgeCases(List.of(judgeCase("0.1 0.2 0.3")))
                .judgeConfig(config)
                .sandboxCaseResults(facts)
                .build();

        assertThat(resolver.resolve(judgeContext, DUMMY_EXECUTOR).getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
    }

    // ===== M4 SPJ 分支 =====

    @Test
    void SPJ_全部用例特判通过聚合为Accepted() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "用户输出A", 30L, 1024L, null),
                fact(0, false, "用户输出B", 40L, 2048L, null));
        SpecialJudgeExecutor spj = new StubExecutor(VerdictEnum.ACCEPTED);

        JudgeInfo result = resolver.resolve(
                spjContext(facts, List.of(judgeCase("标准A"), judgeCase("标准B")), spj, "code"), spj);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly(VerdictEnum.ACCEPTED, VerdictEnum.ACCEPTED);
    }

    @Test
    void SPJ_某用例特判判WA即返回WrongAnswer() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "用户输出", 30L, 1024L, null));
        SpecialJudgeExecutor spj = new StubExecutor(VerdictEnum.WRONG_ANSWER);

        JudgeInfo result = resolver.resolve(
                spjContext(facts, List.of(judgeCase("标准")), spj, "code"), spj);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.WRONG_ANSWER);
    }

    @Test
    void SPJ_某用例特判判PE即返回PresentationError() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "用户输出", 30L, 1024L, null));
        SpecialJudgeExecutor spj = new StubExecutor(VerdictEnum.PRESENTATION_ERROR);

        JudgeInfo result = resolver.resolve(
                spjContext(facts, List.of(judgeCase("标准")), spj, "code"), spj);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.PRESENTATION_ERROR);
    }

    @Test
    void SPJ_特判程序自身崩溃返回SystemError不怪用户() {
        List<SandboxCaseResult> facts = List.of(fact(0, false, "用户输出", 30L, 1024L, null));
        SpecialJudgeExecutor spj = new StubExecutor(VerdictEnum.SYSTEM_ERROR);

        JudgeInfo result = resolver.resolve(
                spjContext(facts, List.of(judgeCase("标准")), spj, "code"), spj);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void SPJ_配置缺失spjCode时回落标准比对() {
        // compareMode=SPJ 但 spjCode 为空 → 不走特判，按标准比对（输出对不上 → WA）
        List<SandboxCaseResult> facts = List.of(fact(0, false, "错误答案\n", 30L, 1024L, null));
        SpecialJudgeExecutor spj = new StubExecutor(VerdictEnum.ACCEPTED); // 不应被调用

        JudgeInfo result = resolver.resolve(
                spjContext(facts, List.of(judgeCase("标准")), spj, ""), spj);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.WRONG_ANSWER);
    }

    // ===== M7-5 T3：真实特判执行器接线回归（M4 用例全部 stub 执行器，绕过真实透传） =====

    @Test
    void SPJ_真实执行器被委派且收到正确参数_合法输出判Accepted() {
        // 用真实 SandboxSpecialJudgeExecutor 接线（非 StubExecutor），沙箱侧回 exitCode=0（特判通过）。
        // 同时断言：resolver 确实把 [标准答案, 用户输出] 透传给真实执行器 —— 这正是 M4 全 stub 时漏测的装配层。
        Sandbox sandbox = mock(Sandbox.class);
        SandboxCaseResult singleFact = fact(0, false, "用户输出", 30L, 1024L, null);
        when(sandbox.executeCode(any(ExecuteCodeRequest.class)))
                .thenReturn(ExecuteCodeResponse.builder().caseResults(List.of(singleFact)).build());
        ArgumentCaptor<ExecuteCodeRequest> captor = ArgumentCaptor.forClass(ExecuteCodeRequest.class);
        SpecialJudgeExecutor real = new SandboxSpecialJudgeExecutor();
        try (MockedStatic<SandboxFactory> ms = mockStatic(SandboxFactory.class)) {
            ms.when(() -> SandboxFactory.newInstance(any())).thenReturn(sandbox);
            JudgeContext ctx = spjContext(
                    List.of(singleFact), List.of(judgeCase("标准答案")), real, "spj-src");
            JudgeInfo result = resolver.resolve(ctx, real);
            assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
            // resolver 仅当 spjMode 成立才会委派执行器；若回落标准比对，verify 会失败
            verify(sandbox).executeCode(captor.capture());
        }
        ExecuteCodeRequest req = captor.getValue();
        // 关键断言：fileArgs = [标准答案, 用户输出]，顺序与数量都锁定（沙箱物化成文件后按路径传给 checker）
        assertThat(req.getFileArgs()).containsExactly("标准答案", "用户输出");
        assertThat(req.getInputList()).containsExactly("1"); // judgeCase 的 input 作为 checker 的 stdin
    }

    // ===== 严格模式（正式判题）：缺期望输出必须显式失败，不得落进中性态 =====

    /** 正式判题上下文：requireExpectedOutput=true（对应 judgeSubmission）。 */
    private JudgeContext strictContext(List<SandboxCaseResult> facts, List<TestCaseData> cases) {
        JudgeContext judgeContext = context(facts, cases);
        judgeContext.setRequireExpectedOutput(true);
        return judgeContext;
    }

    @Test
    void 严格模式下缺少期望输出判SystemError并写明原因() {
        // expectedOutput=null 正是「题库 JSON key 与模型字段对不上」时的真实形态
        List<SandboxCaseResult> facts = List.of(fact(0, false, "3\n", 30L, 1024L, null));

        JudgeInfo result = resolver.resolve(strictContext(facts, List.of(judgeCase(null))), DUMMY_EXECUTOR);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
        assertThat(result.getDetail()).contains("缺少期望输出").contains("expectedOutput");
        // 不再产出「中性态」的 null status —— 配置错误必须是可诊断的显式失败
        assertThat(result.getCaseResults()).isEmpty();
    }

    @Test
    void 同一份缺期望输入在试运行路径仍走中性态() {
        // 与上一条唯一差别：requireExpectedOutput=false（run-with-judge）。
        // 说明严格模式没有破坏试运行「用户自填用例、可以不填期望」的既有语义。
        List<SandboxCaseResult> facts = List.of(fact(0, false, "3\n", 30L, 1024L, null));

        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase(null))), DUMMY_EXECUTOR);

        assertThat(result.getMessage()).isNull();
        assertThat(result.getCaseResults()).hasSize(1);
    }

    @Test
    void 严格模式下题目没有用例判SystemError() {
        JudgeInfo result = resolver.resolve(strictContext(null, List.of()), DUMMY_EXECUTOR);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
        assertThat(result.getDetail()).contains("没有配置判题用例");
    }

    // ===== 落库文本限长：judge_info 是 JSON 列，不能被 1MiB 级 stdout 撑爆 =====

    @Test
    void 超长输出与期望落库前被截断但比对仍用原值() {
        String huge = "x".repeat(20000);
        List<SandboxCaseResult> facts = List.of(fact(0, false, huge, 30L, 1024L, null));

        JudgeInfo result = resolver.resolve(context(facts, List.of(judgeCase(huge))), DUMMY_EXECUTOR);

        assertThat(result.getCaseResults().get(0).getOutput())
                .hasSizeLessThan(huge.length())
                .endsWith("...(truncated)");
        // 截断只作用于回传/落库副本；比对用原值 → 内容相同仍判 AC
        assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
    }
}
