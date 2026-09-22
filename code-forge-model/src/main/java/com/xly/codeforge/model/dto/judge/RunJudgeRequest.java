package com.xly.codeforge.model.dto.judge;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 试运行判题请求（不落库、不产生提交记录，但会判对错）。
 *
 * <p>用于 {@code POST /submission/run-with-judge} —— 题目详情页「运行」按钮的判题版：
 * 用户编辑/套用可编辑用例（输入 + 期望输出），后端调沙箱执行并判题，返回逐用例 verdict，
 * 但不写数据库、不进提交记录。</p>
 *
 * <p>与正式提交（{@code SubmissionCreateRequest}）的区别：本类<b>不</b>产生 submission，
 * 用例也由前端带来（可编辑），而非从题目库取。</p>
 */
@Data
public class RunJudgeRequest implements Serializable {

    /**
     * 题目 id：用于取真实判题配置（时间/内存限制、SPJ 程序）。
     * 可为空 —— 空表示「纯试跑一段代码」，用默认限制兜底。
     */
    private Long questionId;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 编程语言（须为 {@code SubmissionLanguageEnum} 支持的取值）
     */
    private String language;

    /**
     * 可编辑用例列表（输入 + 期望输出），按序与沙箱逐用例对齐。
     * 期望输出留空表示该用例不判对错（中性「已执行」）。
     */
    private List<RunCase> cases;

    @Serial
    private static final long serialVersionUID = 1L;
}
