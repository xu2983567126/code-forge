package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.model.enums.VerdictEnum;

/**
 * 特判（SPJ）执行器。
 *
 * <p>当题目 {@code compareMode == SPJ} 时，标准输出比对无法判定对错（输出顺序任意、自定义规则等），
 * 由题目自带的特殊判定程序（{@code spjCode}）决定每个用例的对错。</p>
 *
 * <p>契约：把「测试输入、标准答案、用户输出」交给特判程序，由其退出码给出结论：
 * 0=通过(ACCEPTED)、1=答案不符(WRONG_ANSWER)、2=格式问题(PRESENTATION_ERROR)，
 * 其余/超时/崩溃=特判程序自身故障(SYSTEM_ERROR，不归咎用户)。</p>
 */
public interface SpecialJudgeExecutor {

    /**
     * 判定单个用例。
     *
     * @param input       测试用例输入（作为特判程序 stdin）
     * @param expected    标准答案（作为 argv[0]）
     * @param userOutput  用户程序输出（作为 argv[1]）
     * @param spjCode     特判程序源码
     * @param spjLanguage 特判程序语言
     * @return 该用例的结论（{@link VerdictEnum}：ACCEPTED / WRONG_ANSWER / PRESENTATION_ERROR / SYSTEM_ERROR）
     */
    VerdictEnum judge(String input, String expected, String userOutput,
                              String spjCode, String spjLanguage);
}
