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
 * {@link com.xly.codeforge.model.enums.JudgeInfoMessageEnum#getValue()} 的英文 verdict
 * （如 {@code Accepted} / {@code Wrong Answer}），前端可直接用既有的 {@code JUDGE_RESULT} 映射上色。</p>
 *
 * @see JudgeInfo
 */
@Data
public class JudgeCaseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 该用例的判定结论（verdict 枚举 value，如 Accepted / Wrong Answer）
     */
    private String status;

    /**
     * 执行时间（ms）
     */
    private Long time;

    /**
     * 消耗内存（KB）
     */
    private Long memory;
}
