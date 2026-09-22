package com.xly.codeforge.model.dto.submission;

import com.xly.codeforge.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 查询请求
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubmissionQueryRequest extends PageRequest implements Serializable {

    /**
     * 编程语言
     */
    private String language;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 提交状态
     */
    private Integer status;

    /**
     * 判题结果（verdict），用于精确筛选单个结论
     *
     * <p>取值见 {@link com.xly.codeforge.model.enums.VerdictEnum}，如 {@code ACCEPTED}。</p>
     */
    private String verdict;

    /**
     * 判题结果（verdict）列表，用于「只看错误类提交」这类多选筛选
     *
     * <p>与 {@link #verdict} 的关系：两者可同时传入，条件为 AND；
     * 前端做「全部 / 通过 / 未通过」分段切换时用本字段传多个 code。</p>
     */
    private List<String> verdicts;

    /**
     * 提交者的 ID
     */
    private Long userId;

    @Serial
    private static final long serialVersionUID = 1L;
}