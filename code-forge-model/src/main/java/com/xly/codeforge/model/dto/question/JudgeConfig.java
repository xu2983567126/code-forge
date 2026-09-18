package com.xly.codeforge.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目配置
 */

@Data
public class JudgeConfig implements Serializable {

    /**
     * 时间限制（ms）
     */
    private Long timeLimit;

    /**
     * 空间限制（KB）
     */
    private Long memoryLimit;

    /**
     * 堆栈限制（kB）
     */
    private Long stackLimit;

    private static final long serialVersionUID = 1L;
}
