package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JudgeContext {

    /**
     * 提交的语言，用于策略
     */
    private String language;

    /**
     * 测试用例（判题侧契约 {@code TestCaseData}，与存储无关；由 TestCaseProvider 提供）
     */
    private List<TestCaseData> judgeCases;

    /**
     * 预设的限制
     */
    private JudgeConfig judgeConfig;

    /**
     * 沙箱回传的逐用例执行事实（判题结论的唯一依据；缺失即链路故障）
     */
    private List<SandboxCaseResult> sandboxCaseResults;

    /**
     * 编译是否失败（沙箱事实字段；null 视为 false）
     */
    private Boolean compileError;

    /**
     * 沙箱链路是否发生系统错误（null 视为 false）
     */
    private Boolean systemError;

    /**
     * 特判程序源码（compareMode=SPJ 时逐用例交由沙箱执行）
     */
    private String spjCode;

    /**
     * 特判程序语言，对齐 submission.language
     */
    private String spjLanguage;

    /**
     * 沙箱回传的原始结论文案（编译/系统错误的 stderr / 异常栈）。
     *
     * <p>{@link VerdictResolver} 只在编译失败 / 系统错误时把它回写到 {@code JudgeInfo.detail}，
     * 供前端展示「为什么错」；常规用例判定不关心它，避免污染逐用例 message。</p>
     */
    private String sandboxMessage;

    /**
     * 严格模式：正式判题（有 submission 落库）要求每个用例都带期望输出。
     *
     * <p>缺失即题目配置有问题 —— 典型成因是 {@code judge_case} 的 JSON key 与
     * {@link com.xly.codeforge.model.dto.question.JudgeCase} 字段不一致，反序列化时被静默丢弃。
     * 此时显式判 {@code SYSTEM_ERROR} 并把原因写进 {@code JudgeInfo.detail}，
     * 而不是落进中性态：中性态是给「试运行用户没填期望输出」用的，
     * 让配置错误借道中性态只会产出「逐用例未知 + verdict 系统错误」这种无从告警的结果。</p>
     *
     * <p>试运行（{@code run-with-judge}）保持 false：用例由用户手填，允许无期望。</p>
     */
    private boolean requireExpectedOutput;
}
