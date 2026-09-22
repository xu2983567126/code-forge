package com.xly.codeforge.model.enums;

import com.xly.codeforge.model.entity.QuestionBankQuestion;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题单批量操作类型
 *
 * <p>用于 {@code POST /question-bank-question/bulk} 的 {@code action} 字段。</p>
 *
 * <p>用枚举而非裸字符串校验：前端把 {@code ADD} 写成 {@code add} / {@code plus} 时
 * 若静默忽略，用户会以为「点了批量添加但没反应」，是最难排查的一类问题。
 * 这里做大小写不敏感匹配，非法值直接抛参数错误。</p>
 *
 * @author xuxu
 */
@Getter
public enum QuestionBankQuestionActionEnum {

    /**
     * 批量加入题单
     */
    ADD("加入", "ADD"),

    /**
     * 批量移出题单
     */
    REMOVE("移出", "REMOVE");

    private final String text;

    private final String value;

    QuestionBankQuestionActionEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    private static final Map<String, QuestionBankQuestionActionEnum> VALUE_MAP =
        Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                e -> e.name().toLowerCase(),
                e -> e,
                (a, _) -> {
                    throw new IllegalStateException("重复的 value: " + a.getValue().toLowerCase());
                }));

    /**
     * 从配置字符串解析
     */
    public static QuestionBankQuestionActionEnum getEnumByValue(String code) {
        if (ObjectUtils.isEmpty(code)) {
            return null;
        }
        return VALUE_MAP.get(code.toLowerCase().trim());
    }
}
