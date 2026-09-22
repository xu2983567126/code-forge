package com.xly.codeforge.judge.comparator;

/**
 * 标准比对：OJ 最常用口径。
 *
 * <p>判定顺序：</p>
 * <ol>
 *   <li>首尾空白 + 换行符归一后相等 → {@link CompareOutcome#EXACT_MATCH}
 *       （兼容旧逻辑 {@code output.trim().equals(expected.trim())} 的口径，
 *        如 {@code "  2\n\n"} 与 {@code "2"} 判一致）。</li>
 *   <li>否则：所有空白（含内部）压缩为单空格后相等 → {@link CompareOutcome#PRESENTATION_ERROR}
 *       （仅<b>内部</b>空白差异，如 {@code "1  2"} 与 {@code "1 2"}）。</li>
 *   <li>否则 → {@link CompareOutcome#MISMATCH}（实质不同）。</li>
 * </ol>
 */
public class StandardOutputComparator implements OutputComparator {

    @Override
    public CompareOutcome compare(String expected, String actual) {
        if (equalsAfterTrimBothEnds(expected, actual)) {
            return CompareOutcome.EXACT_MATCH;
        }
        if (equalsAfterCollapseWhitespace(expected, actual)) {
            return CompareOutcome.PRESENTATION_ERROR;
        }
        return CompareOutcome.MISMATCH;
    }

    private static boolean equalsAfterTrimBothEnds(String expected, String actual) {
        return normalizeLineEndings(nullToEmpty(expected)).trim()
                .equals(normalizeLineEndings(nullToEmpty(actual)).trim());
    }

    private static boolean equalsAfterCollapseWhitespace(String expected, String actual) {
        return collapseWhitespace(nullToEmpty(expected)).equals(collapseWhitespace(nullToEmpty(actual)));
    }

    /** 换行符归一：{@code \r\n} 与 {@code \r} 统一为 {@code \n}。 */
    static String normalizeLineEndings(String s) {
        return nullToEmpty(s).replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 去掉首尾空白、内部空白压缩为单空格。 */
    static String collapseWhitespace(String s) {
        return normalizeLineEndings(s).trim().replaceAll("\\s+", " ");
    }

    static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
