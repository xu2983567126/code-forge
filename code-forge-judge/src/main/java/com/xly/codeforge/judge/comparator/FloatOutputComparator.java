package com.xly.codeforge.judge.comparator;

/**
 * 浮点比对（自研）：按 token 解析为 double，以相对 epsilon 容差逐数值比较。
 *
 * <p>解决 {@code 0.1 + 0.2 = 0.30000000000000004} 这类浮点精度差异导致的误判。
 * 非数值 token 走字符串精确比较，数值 token 走容差比较；两类 token 数量不同即判实质不同。</p>
 *
 * <p>浮点题不存在「仅格式差异」的语义，故本实现只产出 {@link CompareOutcome#EXACT_MATCH}
 * 或 {@link CompareOutcome#MISMATCH}，不产出 {@link CompareOutcome#PRESENTATION_ERROR}。</p>
 */
public class FloatOutputComparator implements OutputComparator {

    /** 相对容差：以两数绝对值的最大值为基准，差异超过该比例即判不同。 */
    private static final double EPSILON = 1e-6;

    @Override
    public CompareOutcome compare(String expected, String actual) {
        String[] expectedTokens = tokenize(expected);
        String[] actualTokens = tokenize(actual);
        if (expectedTokens.length != actualTokens.length) {
            return CompareOutcome.MISMATCH;
        }
        for (int i = 0; i < expectedTokens.length; i++) {
            Double expectedValue = tryParseDouble(expectedTokens[i]);
            Double actualValue = tryParseDouble(actualTokens[i]);
            if (expectedValue != null && actualValue != null) {
                double scale = Math.max(1.0, Math.max(Math.abs(expectedValue), Math.abs(actualValue)));
                if (Math.abs(expectedValue - actualValue) > EPSILON * scale) {
                    return CompareOutcome.MISMATCH;
                }
            } else if (!expectedTokens[i].equals(actualTokens[i])) {
                return CompareOutcome.MISMATCH;
            }
        }
        return CompareOutcome.EXACT_MATCH;
    }

    private static String[] tokenize(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }
        return s.trim().split("\\s+");
    }

    private static Double tryParseDouble(String token) {
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
