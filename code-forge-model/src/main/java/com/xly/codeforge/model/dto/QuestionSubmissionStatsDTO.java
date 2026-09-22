package com.xly.codeforge.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单道题的提交统计（跨服务传输）
 *
 * <p>取代 {@code question.submit_num} / {@code question.accepted_num} 两个存量计数列：
 * 那两列在运行期没有任何更新点（只有造数脚本 INSERT 时写死 0），前端却按它们渲染通过率，
 * 结果恒为 0%。统计改为「读取时从 {@code submission} 表实时算」，
 * 单一真相源落在提交域，题目域不再维护一份会漂移的副本。</p>
 *
 * <p>为什么是 DTO 而不是 {@code Map<Long, long[]>}：见 {@link SubmissionStatsItemDTO} 的说明。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionSubmissionStatsDTO implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 该题的提交总数（含各种 verdict）
     */
    private Long submitCount;

    /**
     * 该题被判 {@code ACCEPTED} 的提交记录数
     *
     * <p>注意与 {@link SubmissionStatsItemDTO#getAcceptedCount()} 的语义区别：那里是
     * 「用户通过了多少道<b>不同的</b>题」（{@code COUNT(DISTINCT question_id)}），
     * 这里是「这道题通过了几<b>次</b>」。前者用于用户维度，后者用于题目通过率。</p>
     */
    private Long acceptedCount;

    @Serial
    private static final long serialVersionUID = 1L;
}
