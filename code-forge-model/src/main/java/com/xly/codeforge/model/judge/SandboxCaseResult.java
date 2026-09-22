package com.xly.codeforge.model.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙箱回传的单用例<b>执行事实</b>。
 *
 * <p>与沙箱工程里的 {@code CaseResult} 是同构的两份复制类（沙箱是独立工程，不能依赖本模块），
 * 字段名必须逐字对齐 —— 沙箱响应经 JSON 反序列化落入本类，名字对不上会静默丢字段。</p>
 *
 * <p>全部是进程执行的中性观测值，不含结论性文案；verdict 判定归判题策略。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxCaseResult {

    /** 进程退出码（超时被杀时为 -1） */
    private Integer exitCode;

    /** 是否触发沙箱超时被杀 */
    private Boolean timedOut;

    /** 标准输出全文 */
    private String output;

    /** 标准错误全文 */
    private String errorOutput;

    /** 执行耗时（ms） */
    private Long timeMs;

    /**
     * 内存用量（KB）：该用例的<b>堆峰值</b>，由用户程序退出时自报。
     *
     * <p>拿不到时为 null（不是 0）—— 用例被 SIGKILL（超时/OOM）时来不及自报，
     * 这种「未知」必须与「确实用了 0」区分开，否则内存超限永远判不出来。</p>
     */
    private Long memoryKb;

    /** 输出是否因超过单流字节上限被截断（截断后内容不完整，比对结果不可信） */
    private Boolean truncated;
}
