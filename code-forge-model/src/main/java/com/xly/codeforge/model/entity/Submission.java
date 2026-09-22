package com.xly.codeforge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目提交
 * @TableName submission
 */
@TableName(value ="submission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 判题信息（json 对象）
     */
    private String judgeInfo;

    /**
     * 判题状态（0 - 待判题、1 - 判题中、2 - 成功、3 - 失败）
     */
    private Integer status;

    /**
     * 判题结果（verdict）
     *
     * <p>取值见 {@link com.xly.codeforge.model.enums.VerdictEnum}，
     * 如 {@code ACCEPTED} / {@code WRONG_ANSWER}。
     * 与 {@link #status} 的区别：status 描述<b>判题流程</b>走到哪一步，
     * verdict 描述<b>代码本身</b>的判定结论（AC / WA / TLE ...）。</p>
     *
     * <p>之所以从 {@code judgeInfo} JSON 里把结论再抽一列出来：状态筛选与统计
     * （如「只看答案错误的提交」、按 verdict 聚合的通过率）若走 JSON 提取，
     * 无法命中索引，只能全表扫。抽成独立列后可走 {@code idx_status_verdict}。</p>
     */
    private String verdict;

    /**
     * 判题代次号：每次抢占判题权 / 租约回收 +1
     *
     * <p>与 {@link #currentAttemptId} 组成双轴 fencing：单靠代次号分不清「同一次尝试的
     * 重复写」与「新一轮抢占」，单靠 attemptId 挡不住「同一次尝试的落后写入」，两者组合才严密。</p>
     */
    private Long generation;

    /**
     * 本次判题 worker 的 UUID（全局唯一）
     *
     * <p>持有租约期间非空；提交回到 WAITING 或抵达终态（SUCCEED/FAILED）后被清 NULL。</p>
     */
    private String currentAttemptId;

    /**
     * 判题租约过期时间（DB 时钟）
     *
     * <p>NULL = 无租约（WAITING 或已终态）。reaper 据此回收「卡在判题中」的僵尸提交，
     * 使其复位为 WAITING 并被重新分发 —— 进程崩溃也不再永久卡死。</p>
     */
    private Date judgingLeaseExpiresAt;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}