package com.xly.codeforge.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件上传业务类型枚举
 *
 */
@Getter
public enum SubmissionLanguageEnum {

    JAVA("java", "java"),
    CPP("cpp", "cpp"),
    GO("go", "go"),
    PYTHON("python", "python"),
    C("c", "c");

    private final String text;

    private final String value;

    SubmissionLanguageEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    private static final Map<String, SubmissionLanguageEnum> VALUE_MAP =
        Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                e -> e.value,
                e -> e,
                (a, _) -> {
                    throw new IllegalStateException("重复的 value: " + a.value);
                }));

    /**
     * 根据 value 获取枚举
     */
    public static SubmissionLanguageEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return VALUE_MAP.get(value);
    }
}
