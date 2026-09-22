package com.xly.codeforge.judge.strategy.impl;

import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.model.enums.VerdictEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultJudgeStrategy} 事实路径的判定顺序回归。
 *
 * <p>核心是验证「先限制后输出」的顺序：老实现先比输出，超时用例的残缺输出
 * 必然对不上答案，导致 TLE / MLE 永远判不出来 —— 本类的第一组用例就是防它回归。</p>
 *
 * <p>{@link DefaultJudgeStrategy} 本身只做一件事：把上下文交给 {@link com.xly.codeforge.judge.strategy.VerdictResolver}
 * （事实归约单点），并在「沙箱既无逐用例事实、也无编译/系统错误标记」时回落 System Error。
 * 具体判定口径见 {@link com.xly.codeforge.judge.strategy.VerdictResolver} 的覆盖。</p>
 */
class DefaultJudgeStrategyTest {

    private static final long TIME_LIMIT = 1000L;
    private static final long MEMORY_LIMIT = 102400L;

    private final DefaultJudgeStrategy strategy = new DefaultJudgeStrategy();

    private JudgeContext context(List<SandboxCaseResult> facts, List<TestCaseData> cases) {
        JudgeConfig config = new JudgeConfig();
        config.setTimeLimit(TIME_LIMIT);
        config.setMemoryLimit(MEMORY_LIMIT);
        return JudgeContext.builder()
                .judgeCases(cases)
                .judgeConfig(config)
                .sandboxCaseResults(facts)
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
                                   long timeMs, long memoryKb) {
        return SandboxCaseResult.builder()
                .exitCode(exitCode)
                .timedOut(timedOut)
                .output(output)
                .timeMs(timeMs)
                .memoryKb(memoryKb)
                .truncated(false)
                .build();
    }

    @Test
    void 全部通过时聚合为ACCEPTED_耗时与内存均取峰值() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 1024L),
                fact(0, false, "3\n", 40L, 2048L));
        List<TestCaseData> cases = List.of(judgeCase("2"), judgeCase("3"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
        // time / memory 都取逐用例峰值（与 UltiCode 单值语义一致），不被用例数放大
        assertThat(result.getTime()).isEqualTo(40L);
        assertThat(result.getMemory()).isEqualTo(2048L);
        assertThat(result.getCaseResults())
                .hasSize(2)
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly(VerdictEnum.ACCEPTED, VerdictEnum.ACCEPTED);
    }

    @Test
    void 沙箱超时被杀时判TLE即使输出对不上() {
        // 超时进程的输出是残缺的，老实现先比输出会错判成 Wrong Answer
        List<SandboxCaseResult> facts = List.of(
                fact(-1, true, "任意残缺输出", 5000L, 1024L));
        List<TestCaseData> cases = List.of(judgeCase("2"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.TIME_LIMIT_EXCEEDED);
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly(VerdictEnum.TIME_LIMIT_EXCEEDED);
        assertThat(result.getTime()).isNull();
    }

    @Test
    void 未触发沙箱超时但超过题目时限时判TLE() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 1200L, 1024L));
        List<TestCaseData> cases = List.of(judgeCase("2"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void 退出码非零时判运行时错误() {
        List<SandboxCaseResult> facts = List.of(
                fact(1, false, "", 50L, 1024L));
        List<TestCaseData> cases = List.of(judgeCase("2"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.RUNTIME_ERROR);
    }

    @Test
    void 内存超限时判MLE() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 200000L));
        List<TestCaseData> cases = List.of(judgeCase("2"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void 输出不匹配时判WA且明细含出错用例() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 1024L),
                fact(0, false, "错误答案\n", 30L, 1024L));
        List<TestCaseData> cases = List.of(judgeCase("2"), judgeCase("3"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.WRONG_ANSWER);
        assertThat(result.getCaseResults())
                .extracting(JudgeCaseResult::getStatus)
                .containsExactly(VerdictEnum.ACCEPTED, VerdictEnum.WRONG_ANSWER);
    }

    @Test
    void 输出带首尾空白时按修剪后比对() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "  2\n\n", 30L, 1024L));
        List<TestCaseData> cases = List.of(judgeCase("2"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
    }

    @Test
    void 截断的输出不可信直接判WA() {
        List<SandboxCaseResult> facts = List.of(
                SandboxCaseResult.builder()
                        .exitCode(0).timedOut(false).output("2").truncated(true)
                        .timeMs(30L).memoryKb(1024L).build());
        List<TestCaseData> cases = List.of(judgeCase("2"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.WRONG_ANSWER);
    }

    @Test
    void 编译失败判COMPILE_ERROR即使没有任何事实明细() {
        // 编译失败时沙箱没有逐用例事实，老实现此处 NPE 落成 System Error
        JudgeContext judgeContext = context(null, List.of(judgeCase("2")));
        judgeContext.setCompileError(true);

        JudgeInfo result = strategy.doJudge(judgeContext);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.COMPILE_ERROR);
    }

    @Test
    void 沙箱系统错误标记优先于其它判定() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 1024L));
        JudgeContext judgeContext = context(facts, List.of(judgeCase("2")));
        judgeContext.setSystemError(true);

        JudgeInfo result = strategy.doJudge(judgeContext);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void 用例数与事实数不一致时判SystemError() {
        List<SandboxCaseResult> facts = List.of(
                fact(0, false, "2\n", 30L, 1024L));
        List<TestCaseData> cases = List.of(judgeCase("2"), judgeCase("3"));

        JudgeInfo result = strategy.doJudge(context(facts, cases));

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }

    @Test
    void 既无事实也无编译系统错误标记时回落SystemError() {
        // 沙箱未升级到事实模型、或调用失败 → 没有 caseResults / compileError / systemError，
        // 判题策略据此判 System Error，不让提交永远卡在「判题中」。
        JudgeContext judgeContext = context(List.of(), List.of(judgeCase("2")));

        JudgeInfo result = strategy.doJudge(judgeContext);

        assertThat(result.getMessage()).isEqualTo(VerdictEnum.SYSTEM_ERROR);
    }
}
