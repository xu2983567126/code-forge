package com.xly.codeforge.judge.testcase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 判题侧测试用例契约（与存储无关）。
 *
 * <p>判题逻辑只认这个类型，不认题目服务的 DB DTO（{@code JudgeCase}）。无论用例来自数据库、
 * 对象存储，还是将来真·文件判题，落到判题路径都是 {@code TestCaseData}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseData implements Serializable {

    /**
     * 用例序号，从 0 升序，用于保序与逐用例对齐
     */
    private int index;

    /**
     * 输入内容；普通题经 stdin 喂沙箱，对象存储题也是读成字符串后再走 stdin
     */
    private String input;

    /**
     * 期望输出，供判题比对
     */
    private String expectedOutput;
}
