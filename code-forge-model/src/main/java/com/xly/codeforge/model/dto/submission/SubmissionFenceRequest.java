package com.xly.codeforge.model.dto.submission;

import lombok.Data;

import java.io.Serializable;

/**
 * 判题 fencing 请求（judge → submission 内部接口）
 *
 * <p>judge-service 只传 {@code generation} / {@code attemptId}，不直接拼 SQL——
 * 真正的 CAS 落在 submission-service 的 mapper 里，保证「只有持租约且代次匹配者能写回」。</p>
 *
 * @author xuxu
 */
@Data
public class SubmissionFenceRequest implements Serializable {

    /**
     * 提交记录 id
     */
    private Long id;

    /**
     * 本次判题 worker 的 UUID（全局唯一）
     */
    private String attemptId;

    /**
     * 抢占时读取到的代次号；CAS 必须带它，否则挡不住「多实例 / 僵尸 worker 复活」的覆盖写
     */
    private long generation;

    /**
     * 租约时长（秒）；submission-service 用 DB 时钟 {@code NOW()+INTERVAL} 计算过期点
     */
    private int ttlSeconds;
}
