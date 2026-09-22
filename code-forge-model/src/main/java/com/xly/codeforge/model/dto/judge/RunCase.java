package com.xly.codeforge.model.dto.judge;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 试运行判题的单个用例：输入 + 期望输出。
 *
 * <p>与正式判题的 {@code TestCaseData} 同构，但独立成类以便前端自由编辑、避免与
 * 题目库用例的存储形态耦合。</p>
 */
@Data
public class RunCase implements Serializable {

    /**
     * 用例输入（作为标准输入交给沙箱）
     */
    private String input;

    /**
     * 期望输出（供判题比对）
     *
     * <p>可编辑；留空表示不判对错，run 结果按中性「已执行」展示。</p>
     */
    private String expectedOutput;

    @Serial
    private static final long serialVersionUID = 1L;
}
