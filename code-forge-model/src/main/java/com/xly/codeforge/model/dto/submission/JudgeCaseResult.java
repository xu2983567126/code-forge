package com.xly.codeforge.model.dto.submission;

import lombok.Data;

import java.io.Serializable;

/**
 * 单测试用例的判定结果。
 *
 * <p>用于前端渲染「verdict 卡片」（逐用例展示通过 / 答案错误 / 超时 / 内存溢出）
 * 与「性能分布」（逐用例的耗时 / 内存）。{@link JudgeInfo#getCaseResults()} 是它的集合。</p>
 *
 * <p>{@code status} 取值与 {@link JudgeInfo#getMessage()} 同构 —— 都是
 * {@link com.xly.codeforge.model.enums.VerdictEnum#getCode()} 的英文 verdict 码
 * （如 {@code ACCEPTED} / {@code WRONG_ANSWER}），前端可直接用既有的 {@code JUDGE_RESULT} 映射上色。</p>
 *
 * @see JudgeInfo
 */
@Data
public class JudgeCaseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 该用例的判定结论（{@link VerdictEnum}，序列化后为 {@code code} 字符串，如 {@code ACCEPTED} / {@code WRONG_ANSWER}）。
     */
    private com.xly.codeforge.model.enums.VerdictEnum status;

    /**
     * 该用例的输入（stdin 全文，来自判题侧契约 TestCaseData）。
     *
     * <p>与 {@link #output}、{@link #expectedOutput} 同源带出，前端按用例对象直接渲染，
     * 不再依赖前端 cases 与后端 caseResults 同序对齐（消除顺序耦合）。</p>
     */
    private String input;

    /**
     * 执行时间（ms）
     */
    private Long time;

    /**
     * 消耗内存（KB）
     */
    private Long memory;

    /**
     * 该用例失败时的错误原因（如运行时异常的 stderr 信息）。
     * 仅失败时填写，供前端 verdict 卡片展示「为什么错」；成功用例为空。
     */
    private String errorMessage;

    /**
     * 该用例的实际输出（用户程序打印到 stdout 的全文）。
     *
     * <p>供前端 verdict 卡片并排展示「实际输出 vs 期望输出」。后端统一在此回写，
     * 提交详情与试运行结果复用同一字段。</p>
     */
    private String output;

    /**
     * 该用例的期望输出（标准答案，来自判题侧契约 TestCaseData 或试运行用户自填）。
     *
     * <p>与 {@link #output} 同源带出，逐用例回写；空白时置 null（试运行自定义用例无期望输出），
     * 前端据此回落中性态展示。前端不再用前端 cases[i].expectedOutput 按序对齐。</p>
     */
    private String expectedOutput;
}
