package com.xly.codeforge.model.dto.submission;

import lombok.Data;

import java.util.List;

/**
 * 判题结果信息（聚合结论 + 逐用例明细）。
 *
 * <p>{@code message / time / memory} 是该次提交的<b>聚合结论</b>：
 * {@code message} 为最终 verdict（取最差用例的结论），{@code time / memory} 为各用例之和。</p>
 *
 * <p>{@link #caseResults} 是<b>逐测试用例明细</b>，供前端渲染 verdict 卡片与性能分布；
 * 老数据（本字段引入前落库的 {@code judge_info}）反序列化后为 {@code null}，前端需判空。</p>
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息（最终 verdict，如 Accepted / Wrong Answer）
     */
    private String message;

    /**
     * 执行时间（ms，各用例之和）
     */
    private Long time;

    /**
     * 消耗内存（KB，各用例之和）
     */
    private Long memory;

    /**
     * 逐测试用例判定结果（verdict 卡片 / 性能分布的数据来源）
     */
    private List<JudgeCaseResult> caseResults;
}
