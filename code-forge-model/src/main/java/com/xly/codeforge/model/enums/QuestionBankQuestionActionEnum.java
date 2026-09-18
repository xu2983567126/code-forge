package com.xly.codeforge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

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
public enum QuestionBankQuestionActionEnum {

    /** 批量加入题单 */
    ADD("加入", "ADD"),

    /** 批量移出题单 */
    REMOVE("移出", "REMOVE");

    private final String text;

    private final String value;

    QuestionBankQuestionActionEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举（大小写不敏感）
     *
     * @param value 前端传入的 action（如 {@code add} / {@code ADD}）
     * @return 匹配的枚举，无匹配返回 null
     */
    public static QuestionBankQuestionActionEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (QuestionBankQuestionActionEnum anEnum : QuestionBankQuestionActionEnum.values()) {
            if (anEnum.value.equalsIgnoreCase(value.trim())) {
                return anEnum;
            }
        }
        return null;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }
}
