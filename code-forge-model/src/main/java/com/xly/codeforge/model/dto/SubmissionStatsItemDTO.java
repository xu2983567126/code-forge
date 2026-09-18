package com.xly.codeforge.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个用户的提交统计（跨服务传输）
 *
 * <p>为什么是一个朴素 DTO 而不是直接返回 {@code Map<Long, long[]>}：
 * 数组下标没有任何语义，第二个服务接入时没人记得 {@code [0]} 是提交数还是通过数。
 * 字段名本身就是文档。</p>
 *
 * @author xuxu
 */
@Data
public class SubmissionStatsItemDTO implements Serializable {

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 提交总数
     */
    private Long submitCount;

    /**
     * 通过的题目数（{@code COUNT(DISTINCT question_id)}，不是 AC 次数）
     */
    private Long acceptedCount;

    @Serial
    private static final long serialVersionUID = 1L;
}
