package com.xly.codeforge.model.dto.question;

import lombok.Data;

/**
 * 判题信息
 */

@Data
public class JudgeCase {

    /**
     * 输入用例
     */
    private String input;

    /**
     * 期望输出（标准答案）。判题链路以此与用户程序的实际输出比对。
     * 命名刻意与沙箱/返回侧的 {@code output}（用户实际输出）区分，避免"以为 output 是用户输出"的误读陷阱。
     */
    private String expectedOutput;
}
