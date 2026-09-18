package com.xly.codeforge.model.dto.judge;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 试运行代码请求
 *
 * <p>用于 {@code POST /submission/run} —— 用户在题目详情页点「运行」，
 * 用自填的样例输入试一下代码，不产生提交记录。</p>
 *
 * <p>与正式提交（{@code SubmissionAddRequest}）的差别：本类<b>不</b>含 {@code questionId}
 * 之外的业务字段，也不写库；但为了让「运行」也能校验代码是否符合题目要求，
 * 仍保留 {@code questionId}（后端据此取题目的判题配置与用例）。</p>
 *
 * @author xuxu
 */
@Data
public class RunCodeRequest implements Serializable {

    /**
     * 题目 id
     *
     * <p>用于取题目的判题配置（时间/内存限制）。可为空 —— 空表示「纯试跑一段代码」，
     * 此时用默认限制。</p>
     */
    private Long questionId;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 样例输入（用户自填）
     *
     * <p>为空时用空串跑一次；非空时按行作为 stdin 内容。</p>
     */
    private String input;

    @Serial
    private static final long serialVersionUID = 1L;
}
