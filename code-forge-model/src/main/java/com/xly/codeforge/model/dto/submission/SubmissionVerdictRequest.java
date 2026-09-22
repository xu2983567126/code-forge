package com.xly.codeforge.model.dto.submission;

import lombok.Data;

import java.io.Serializable;

/**
 * 判题结论写回请求（judge → submission 内部接口，fenced 版本）
 *
 * <p>写回是 CAS：必须同时匹配 {@code generation} + {@code attemptId} 才落库，
 * 返回 0 表示结果已 stale（被 reaper 回收重派），调用方应丢弃不写。</p>
 *
 * @author xuxu
 */
@Data
public class SubmissionVerdictRequest implements Serializable {

    /**
     * 提交记录 id
     */
    private Long id;

    /**
     * 抢占时读取到的代次号
     */
    private long generation;

    /**
     * 本次判题 worker 的 UUID（必须与租约持有者一致）
     */
    private String attemptId;

    /**
     * 终态：2=SUCCEED / 3=FAILED（对应 {@code SubmissionStatusEnum}）
     */
    private int status;

    /**
     * 判题结论 code（对应 {@code VerdictEnum}，如 ACCEPTED / WRONG_ANSWER）
     */
    private String verdict;

    /**
     * 判题信息 JSON（逐用例耗时 / 内存 / message）
     */
    private String judgeInfo;
}
