package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.judge.comparator.CompareOutcome;
import com.xly.codeforge.judge.comparator.OutputComparator;
import com.xly.codeforge.judge.comparator.OutputComparatorFactory;
import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.CompareMode;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.xly.codeforge.model.enums.VerdictEnum.ACCEPTED;
import static com.xly.codeforge.model.enums.VerdictEnum.COMPILE_ERROR;
import static com.xly.codeforge.model.enums.VerdictEnum.MEMORY_LIMIT_EXCEEDED;
import static com.xly.codeforge.model.enums.VerdictEnum.PRESENTATION_ERROR;
import static com.xly.codeforge.model.enums.VerdictEnum.RUNTIME_ERROR;
import static com.xly.codeforge.model.enums.VerdictEnum.SYSTEM_ERROR;
import static com.xly.codeforge.model.enums.VerdictEnum.TIME_LIMIT_EXCEEDED;
import static com.xly.codeforge.model.enums.VerdictEnum.WRONG_ANSWER;

/**
 * 判题结论单点归约器。
 *
 * <p>输入沙箱回传的<b>逐用例执行事实</b> + 题目配置 + 比对策略，输出<b>唯一</b> verdict。
 * 把原散在 {@code DefaultJudgeStrategy.doJudgeByFacts} 的事实路径判定逻辑收敛到此处，
 * 成为判题结论的唯一真相源 —— 后续 M4（SPJ 分支）、M7（状态枚举收敛）都在此扩展。</p>
 *
 * <p>判定顺序是正确性的关键：<b>先判限制与错误，最后才比对输出</b>。超时用例的程序输出是残缺 /
 * 任意的，先比输出会把超时错判成答案错误 —— 老实现（先输出后限制）正是因此让 TLE / MLE 永远不可达。</p>
 */
public class VerdictResolver {

    /** 失败用例的 errorMessage 最大长度，防止超大 stderr 撑爆 judgeInfo 字段。 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /**
     * 逐用例 input / expectedOutput / output 回写时的字符上限。
     *
     * <p>沙箱单流输出上限是 1 MiB，而 {@code judge_info} 是 JSON 列 —— 原样回写会把列撑爆
     * （{@code text} 只有 64 KB，超长写入在 {@code STRICT_TRANS_TABLES} 下直接报 1406）。
     * 前端展示也不需要整段 stdout，截断即可。</p>
     */
    private static final int MAX_CASE_TEXT_LENGTH = 8192;

    /**
     * 事实路径归约。
     *
     * @param judgeContext       含 sandboxCaseResults / judgeConfig / judgeCases / compileError / systemError / spjCode / spjLanguage
     * @param specialJudgeExecutor 特判执行器（{@code compareMode=SPJ} 时使用；非 SPJ 题不会被调用，可传 null）
     * @return 唯一 verdict（写入 {@code JudgeInfo.message} 与逐用例 {@code caseResults}）
     */
    public JudgeInfo resolve(JudgeContext judgeContext, SpecialJudgeExecutor specialJudgeExecutor) {
        List<TestCaseData> judgeCases = judgeContext.getJudgeCases();
        List<SandboxCaseResult> facts = judgeContext.getSandboxCaseResults();
        JudgeConfig judgeConfig = judgeContext.getJudgeConfig();
        JudgeInfo judgeInfoResult = new JudgeInfo();
        // 逐用例明细：聚合结论的同时同步构建，供前端 verdict 卡片 / 性能分布使用
        List<JudgeCaseResult> caseResults = new ArrayList<>();
        // 聚合口径（参照 UltiCode）：time / memory 都取<b>逐用例峰值</b>（最慢单用例耗时、最高堆峰值），
        // 而非耗时求和。两者同义「整段判题里最坏的那一个用例」，口径一致、且不会被用例数放大。
        Long time = 0L;
        Long memory = 0L;
        // 是否出现过「可判定」用例（带期望输出）。试运行可能全用例无期望输出，此时聚合结论无意义。
        boolean anyComparable = false;

        // 0. 严格模式（正式判题）：每个用例都必须带期望输出，否则题目配置有问题。
        //    必须在其它判定之前 —— 缺期望输出时无论沙箱跑成什么样都判不出对错，
        //    先报配置错误能一步定位，也避免被后面的 compileError/systemError 掩盖。
        if (judgeContext.isRequireExpectedOutput()) {
            if (judgeCases.isEmpty()) {
                judgeInfoResult.setDetail("题目没有配置判题用例，无法判题");
                return finishWith(judgeInfoResult, caseResults, SYSTEM_ERROR);
            }
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < judgeCases.size(); i++) {
                if (StringUtils.isBlank(judgeCases.get(i).getExpectedOutput())) {
                    missing.add(i + 1);
                }
            }
            if (!missing.isEmpty()) {
                judgeInfoResult.setDetail("题目用例缺少期望输出：第 " + missing
                        + " 个用例的 expectedOutput 为空，无法判定对错。"
                        + "请检查题目 judge_case 的字段名是否为 expectedOutput（历史数据可能仍是 output）");
                return finishWith(judgeInfoResult, caseResults, SYSTEM_ERROR);
            }
        }

        // 1. 系统错误：沙箱链路自身故障，与用户代码无关
        if (Boolean.TRUE.equals(judgeContext.getSystemError())) {
            judgeInfoResult.setDetail(judgeContext.getSandboxMessage());
            return finishWith(judgeInfoResult, caseResults, SYSTEM_ERROR);
        }
        // 2. 编译错误：编译产物不存在，无从逐用例判定
        if (Boolean.TRUE.equals(judgeContext.getCompileError())) {
            judgeInfoResult.setDetail(judgeContext.getSandboxMessage());
            return finishWith(judgeInfoResult, caseResults, COMPILE_ERROR);
        }
        // 3. 用例数与事实数不一致 = 沙箱回传残缺，属链路故障而非答案错误
        if (facts.size() != judgeCases.size()) {
            return finishWith(judgeInfoResult, caseResults, SYSTEM_ERROR);
        }

        // SPJ 模式：compareMode=SPJ 且题目确实带了特判程序；否则回落标准比对
        String rawMode = judgeConfig == null ? null : judgeConfig.getCompareMode();
        boolean spjConfigured = CompareMode.SPJ.equals(CompareMode.fromCode(rawMode));
        boolean spjMode = spjConfigured && StringUtils.isNotBlank(judgeContext.getSpjCode());

        // SPJ 无 spjCode（配置缺失）→ 拿 STANDARD 比较器兜底；SPJ 有码 → 不走比较器（comparator 置空）
        OutputComparator comparator = spjMode ? null
                : OutputComparatorFactory.getComparator(spjConfigured ? "STANDARD" : rawMode);

        for (int i = 0; i < judgeCases.size(); i++) {
            SandboxCaseResult fact = facts.get(i);
            JudgeCaseResult caseResult = new JudgeCaseResult();
            caseResult.setTime(fact.getTimeMs());
            long memKb = Optional.ofNullable(fact.getMemoryKb()).orElse(0L);
            caseResult.setMemory(memKb);
            // 逐用例回写实际输出（用户程序 stdout 全文），供前端并排展示「实际 vs 期望」。
            // 回写前限长：沙箱单流上限 1 MiB，原样落进 judge_info 会撑爆列；比对用的是原值（见下方 userOutput）
            caseResult.setOutput(truncate(fact.getOutput(), MAX_CASE_TEXT_LENGTH));
            // 逐用例回写输入与期望输出：与「实际输出」同源带出，前端按用例对象直接渲染，
            // 不再依赖 cases 与 caseResults 同序对齐（消除顺序耦合——后端一旦重排用例，按 index 取会错位）。
            caseResult.setInput(truncate(judgeCases.get(i).getInput(), MAX_CASE_TEXT_LENGTH));
            String expected = judgeCases.get(i).getExpectedOutput();
            caseResult.setExpectedOutput(StringUtils.isBlank(expected) ? null
                    : truncate(expected, MAX_CASE_TEXT_LENGTH));

            // 4. 沙箱超时被杀 —— 事实字段优先于耗时推断（被杀进程的退出码/输出都不可信）
            if (Boolean.TRUE.equals(fact.getTimedOut())) {
                return finishWith(judgeInfoResult, append(caseResults, caseResult, TIME_LIMIT_EXCEEDED), TIME_LIMIT_EXCEEDED);
            }
            // 5. 未触发沙箱超时但耗时超过题目限制（沙箱超时是兜底闸，题目限制才是判定依据）
            if (fact.getTimeMs() != null && judgeConfig != null && judgeConfig.getTimeLimit() != null
                    && fact.getTimeMs() > judgeConfig.getTimeLimit()) {
                return finishWith(judgeInfoResult, append(caseResults, caseResult, TIME_LIMIT_EXCEEDED), TIME_LIMIT_EXCEEDED);
            }
            // 6. 运行时错误：退出码非 0 即视为运行失败，错误信息透出供用户定位
            if (fact.getExitCode() == null || fact.getExitCode() != 0) {
                caseResult.setErrorMessage(truncate(fact.getErrorOutput(), MAX_ERROR_MESSAGE_LENGTH));
                return finishWith(judgeInfoResult, append(caseResults, caseResult, RUNTIME_ERROR), RUNTIME_ERROR);
            }
            // 7. 内存超限（沙箱采集不到内存时按 0 计，不会误伤）
            if (judgeConfig != null && judgeConfig.getMemoryLimit() != null && memKb > judgeConfig.getMemoryLimit()) {
                return finishWith(judgeInfoResult, append(caseResults, caseResult, MEMORY_LIMIT_EXCEEDED), MEMORY_LIMIT_EXCEEDED);
            }
            // 8. 输出比对 / 特判。截断的输出内容不完整，比对结果不可信，直接判答案错误
            String userOutput = fact.getOutput();
            // 试运行场景：用例可能不带期望输出（用户手填 / 自定义用例），无法判定对错。
            // 此时走「中性态」—— 仅回显实际输出，不置 pass/fail，也不计入聚合结论。
            // 注意：超时 / 运行错误 / 内存超限（步骤 4-7）不受此影响，仍按事实判定。
            if (StringUtils.isBlank(expected)) {
                caseResult.setStatus(null);
                time = Math.max(time, Optional.ofNullable(fact.getTimeMs()).orElse(0L));
                memory = Math.max(memory, memKb);
                caseResults.add(caseResult);
                continue;
            }
            if (spjMode) {
                anyComparable = true;
                VerdictEnum spj = specialJudgeExecutor.judge(
                        judgeCases.get(i).getInput(), expected, userOutput,
                        judgeContext.getSpjCode(), judgeContext.getSpjLanguage());
                if (spj == ACCEPTED) {
                    // 该用例通过：记账后继续下一用例
                    time = Math.max(time, Optional.ofNullable(fact.getTimeMs()).orElse(0L));
                    memory = Math.max(memory, memKb);
            caseResult.setStatus(ACCEPTED);
            caseResults.add(caseResult);
            continue;
        } else if (spj == PRESENTATION_ERROR) {
                    return finishWith(judgeInfoResult, append(caseResults, caseResult, PRESENTATION_ERROR), PRESENTATION_ERROR);
                } else if (spj == WRONG_ANSWER) {
                    return finishWith(judgeInfoResult, append(caseResults, caseResult, WRONG_ANSWER), WRONG_ANSWER);
                } else {
                    // 特判程序自身故障（退出码非 0/1/2、超时）→ checker 配置错误，不怪用户
                    return finishWith(judgeInfoResult, append(caseResults, caseResult, SYSTEM_ERROR), SYSTEM_ERROR);
                }
            }
            anyComparable = true;
            CompareOutcome outcome = comparator.compare(expected, userOutput);
            if (Boolean.TRUE.equals(fact.getTruncated()) || outcome == CompareOutcome.MISMATCH) {
                return finishWith(judgeInfoResult, append(caseResults, caseResult, WRONG_ANSWER), WRONG_ANSWER);
            } else if (outcome == CompareOutcome.PRESENTATION_ERROR) {
                return finishWith(judgeInfoResult, append(caseResults, caseResult, PRESENTATION_ERROR), PRESENTATION_ERROR);
            }

            time = Math.max(time, Optional.ofNullable(fact.getTimeMs()).orElse(0L));
            memory = Math.max(memory, memKb);
            caseResult.setStatus(ACCEPTED);
            caseResults.add(caseResult);
        }

        // 全用例都无期望输出（纯执行、不可判定对错）→ 聚合结论置空，前端回落「已执行」
        judgeInfoResult.setMessage(anyComparable ? ACCEPTED : null);
        judgeInfoResult.setTime(time);
        judgeInfoResult.setMemory(memory);
        judgeInfoResult.setCaseResults(caseResults);
        return judgeInfoResult;
    }

    private List<JudgeCaseResult> append(List<JudgeCaseResult> caseResults, JudgeCaseResult caseResult,
                                         VerdictEnum status) {
        caseResult.setStatus(status);
        caseResults.add(caseResult);
        return caseResults;
    }

    /**
     * 以给定结论收尾：写入聚合 message 与逐用例明细后返回。
     *
     * <p>失败路径不回填聚合 time/memory —— 只有用例全部通过时总量才有意义，
     * 与前端「失败态只展示结论卡片」的约定一致。</p>
     */
    private JudgeInfo finishWith(JudgeInfo judgeInfoResult, List<JudgeCaseResult> caseResults,
                                 VerdictEnum status) {
        judgeInfoResult.setMessage(status);
        judgeInfoResult.setCaseResults(caseResults);
        return judgeInfoResult;
    }

    /**
     * 超长文本截断并标注，防止单条 judge_info JSON 撑爆数据库列。
     *
     * @param text  原文本；null 原样返回
     * @param limit 字符上限
     * @return 未超长时原样返回，否则截断并追加 {@code ...(truncated)}
     */
    private String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...(truncated)";
    }
}
