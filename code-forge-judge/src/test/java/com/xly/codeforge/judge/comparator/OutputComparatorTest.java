package com.xly.codeforge.judge.comparator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三种比对策略的语义回归。
 *
 * <p>核心验证：标准比对区分「首尾空白差异（一致）/ 内部空白差异（格式错误）/ 实质不同（错误答案）」；
 * 严格比对只归一化换行符；浮点比对按 epsilon 容差；工厂缺省回落标准。</p>
 */
class OutputComparatorTest {

    private final OutputComparator standard = new StandardOutputComparator();
    private final OutputComparator strict = new StrictOutputComparator();
    private final OutputComparator floatCmp = new FloatOutputComparator();

    @Test
    void standard_首尾空白差异视为一致() {
        assertThat(standard.compare("  2\n\n", "2")).isEqualTo(CompareOutcome.EXACT_MATCH);
        assertThat(standard.compare("2\n", "2")).isEqualTo(CompareOutcome.EXACT_MATCH);
        assertThat(standard.compare("1 2", "1 2")).isEqualTo(CompareOutcome.EXACT_MATCH);
    }

    @Test
    void standard_内部空白差异视为格式错误() {
        assertThat(standard.compare("1  2", "1 2")).isEqualTo(CompareOutcome.PRESENTATION_ERROR);
        assertThat(standard.compare("a b c", "a  b  c")).isEqualTo(CompareOutcome.PRESENTATION_ERROR);
    }

    @Test
    void standard_实质不同视为错误答案() {
        assertThat(standard.compare("错误答案", "3")).isEqualTo(CompareOutcome.MISMATCH);
        assertThat(standard.compare("12", "1 2")).isEqualTo(CompareOutcome.MISMATCH);
    }

    @Test
    void standard_null安全() {
        assertThat(standard.compare(null, null)).isEqualTo(CompareOutcome.EXACT_MATCH);
        assertThat(standard.compare("2", null)).isEqualTo(CompareOutcome.MISMATCH);
        assertThat(standard.compare(null, "2")).isEqualTo(CompareOutcome.MISMATCH);
    }

    @Test
    void strict_仅归一化换行符后精确比较() {
        assertThat(strict.compare("2\r\n3", "2\n3")).isEqualTo(CompareOutcome.EXACT_MATCH);
        // 末尾多一个空格 = 严格下不一致
        assertThat(strict.compare("2 ", "2")).isEqualTo(CompareOutcome.MISMATCH);
    }

    @Test
    void float_精度内误差视为一致() {
        assertThat(floatCmp.compare("0.1 0.2 0.3", "0.1 0.2 0.30000000000000004"))
                .isEqualTo(CompareOutcome.EXACT_MATCH);
    }

    @Test
    void float_超容差视为不同() {
        assertThat(floatCmp.compare("0.1 0.2", "0.1 0.3")).isEqualTo(CompareOutcome.MISMATCH);
    }

    @Test
    void float_非数值token按字符串比较() {
        assertThat(floatCmp.compare("yes 1", "yes 1")).isEqualTo(CompareOutcome.EXACT_MATCH);
        assertThat(floatCmp.compare("yes 1", "no 1")).isEqualTo(CompareOutcome.MISMATCH);
    }

    @Test
    void factory_缺省回落标准且按模式选型() {
        assertThat(OutputComparatorFactory.getComparator((String) null)).isInstanceOf(StandardOutputComparator.class);
        assertThat(OutputComparatorFactory.getComparator("STANDARD")).isInstanceOf(StandardOutputComparator.class);
        assertThat(OutputComparatorFactory.getComparator("STRICT")).isInstanceOf(StrictOutputComparator.class);
        assertThat(OutputComparatorFactory.getComparator("FLOAT")).isInstanceOf(FloatOutputComparator.class);
        // 无法识别的模式回落标准，不抛异常
        assertThat(OutputComparatorFactory.getComparator("未知模式")).isInstanceOf(StandardOutputComparator.class);
    }
}
