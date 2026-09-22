package com.xly.codeforge.judge.comparator;

/**
 * 输出比对策略接口。
 *
 * <p>输入标准答案与用户实际输出，产出 {@link CompareOutcome}。实现必须幂等、无副作用，
 * 不依赖题目配置之外的任何状态 —— 题目配置由 {@link OutputComparatorFactory} 在选型时固化进实现。</p>
 */
public interface OutputComparator {

    /**
     * 比对标准答案与用户输出。
     *
     * @param expected 标准答案（可能含换行 / 空白），null 视为空串
     * @param actual   用户实际输出（沙箱 stdout），null 视为空串
     * @return 三档结论，永不为 null
     */
    CompareOutcome compare(String expected, String actual);
}
