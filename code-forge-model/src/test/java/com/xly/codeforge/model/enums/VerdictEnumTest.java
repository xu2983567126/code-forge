package com.xly.codeforge.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VerdictEnum} 作为判题结论的唯一真相源（单枚举），落库的 code 形态与查表校验。
 *
 * <p>早期曾有「judgeInfo.message（展示文案）→ 落库 verdict」的桥接枚举 JudgeVerdictMessageEnum，
 * 现已收敛为单枚举：JudgeInfo.message / 逐用例 status / submission.verdict 列
 * 三者同用 getCode()，不再需要第二套枚举互桥。</p>
 */
class VerdictEnumTest {


    @Test
    void code是稳定紧凑的落库形态() {
        assertThat(VerdictEnum.getCodes())
                .containsExactlyInAnyOrder(
                        "ACCEPTED", "WRONG_ANSWER", "COMPILE_ERROR", "RUNTIME_ERROR",
                        "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED", "PRESENTATION_ERROR",
                        "DANGEROUS_OPERATION", "SYSTEM_ERROR");
        assertThat(VerdictEnum.getEnumByCode("COMPILE_ERROR")).isEqualTo(VerdictEnum.COMPILE_ERROR);
        assertThat(VerdictEnum.getEnumByCode("不存在的")).isNull();
    }
}
