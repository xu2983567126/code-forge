package com.xly.codeforge.model.dto.submission;

import lombok.Data;

import java.util.List;

/**
 * 判题结果信息（聚合结论 + 逐用例明细）。
 *
 * <p>{@code message / time / memory} 是该次提交的<b>聚合结论</b>：
 * {@code message} 为最终 verdict（取最差用例的结论），{@code time / memory} 均取逐用例<b>峰值</b>
 * （最慢单用例耗时、最高堆峰值），与逐用例明细同口径，不随用例数放大。</p>
 *
 * <p>{@link #caseResults} 是<b>逐测试用例明细</b>，供前端渲染 verdict 卡片与性能分布；
 * 老数据（本字段引入前落库的 {@code judge_info}）反序列化后为 {@code null}，前端需判空。</p>
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息（最终 verdict，如 Accepted / Wrong Answer）。
     *
     * <p>类型为 {@link com.xly.codeforge.model.enums.VerdictEnum}：编译期防错，
     * 序列化后仍为 code 字符串（如 {@code "ACCEPTED"}），与前端 {@code JUDGE_RESULT} 映射一致。</p>
     */
    private com.xly.codeforge.model.enums.VerdictEnum message;

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

    /**
     * 编译 / 系统错误时的原始诊断信息（编译器 stderr / 异常栈）。
     *
     * <p>仅失败态填充，供前端「为什么错」展开；常规判定不填充。与逐用例
     * {@code JudgeCaseResult.errorMessage}（运行时错误）是两套口径：前者是判题链路自身故障，
     * 后者是用户程序运行失败。</p>
     */
    private String detail;
}
