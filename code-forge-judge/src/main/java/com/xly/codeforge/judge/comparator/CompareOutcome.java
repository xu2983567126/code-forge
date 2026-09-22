package com.xly.codeforge.judge.comparator;

/**
 * 单次输出比对的三档结论。
 *
 * <p> comparator 只回答「输出对不对、差在哪」，<b>不产出</b> TLE/MLE/RE/CE 这类资源/错误 verdict
 * （那是 {@code VerdictResolver} 的职责）。本枚举把「对」与「错」再细分为三档，
 * 让标准比对能区分「实质错误」与「仅格式/空白错误」。</p>
 */
public enum CompareOutcome {

    /** 按模式判为一致 */
    EXACT_MATCH,

    /** 仅格式 / 空白差异（如双空格 vs 单空格、多余换行），应输出 PRESENTATION_ERROR */
    PRESENTATION_ERROR,

    /** 实质不同，应输出 WRONG_ANSWER */
    MISMATCH
}
