package com.xly.codeforge.model.dto.question;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新请求
 *
 */
@Data
public class QuestionUpdateRequest implements Serializable, QuestionRequest {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表（json 数组）
     */
    private List<String> tags;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 难度：简单/中等/困难
     */
    private String difficulty;

    /**
     * 判题用例（json 数组）
     */
    private List<JudgeCase> judgeCase;

    /**
     * 判题配置（json 对象）
     */
    private JudgeConfig judgeConfig;

    /**
     * 特判程序源码（compareMode=SPJ 时由沙箱执行）
     */
    private String spjCode;

    /**
     * 特判程序语言，对齐 submission.language 取值
     */
    private String spjLanguage;

    /**
     * 判题代码模板（编辑器预置骨架，用户可见）
     */
    private String codeTemplate;

    private static final long serialVersionUID = 1L;
}