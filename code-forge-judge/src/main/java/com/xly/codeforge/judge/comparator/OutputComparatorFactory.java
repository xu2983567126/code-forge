package com.xly.codeforge.judge.comparator;

import com.xly.codeforge.model.enums.CompareMode;

/**
 * 按比对模式产出 {@link OutputComparator} 实现。
 *
 * <p>缺省（null 或无法识别）回落 {@link StandardOutputComparator}，保证任何题目都有可用比对器。</p>
 */
public final class OutputComparatorFactory {

    private OutputComparatorFactory() {
    }

    public static OutputComparator getComparator(CompareMode mode) {
        if (mode == null) {
            mode = CompareMode.STANDARD;
        }
        return switch (mode) {
            case STRICT -> new StrictOutputComparator();
            case FLOAT -> new FloatOutputComparator();
            case SPJ -> throw new UnsupportedOperationException("SPJ 比对由 M4 实现，当前模式不可用");
            default -> new StandardOutputComparator();
        };
    }

    public static OutputComparator getComparator(String modeCode) {
        return getComparator(CompareMode.fromCode(modeCode));
    }
}
