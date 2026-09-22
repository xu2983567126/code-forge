package com.xly.codeforge.judge.comparator;

/**
 * 严格比对：仅归一化换行符，整串精确比较（无任何空白容忍）。
 *
 * <p>用于要求输出格式完全一致的题目（如输出即为特定分隔串的场景）。</p>
 */
public class StrictOutputComparator implements OutputComparator {

    @Override
    public CompareOutcome compare(String expected, String actual) {
        String expectedNorm = StandardOutputComparator.normalizeLineEndings(expected);
        String actualNorm = StandardOutputComparator.normalizeLineEndings(actual);
        return expectedNorm.equals(actualNorm) ? CompareOutcome.EXACT_MATCH : CompareOutcome.MISMATCH;
    }
}
