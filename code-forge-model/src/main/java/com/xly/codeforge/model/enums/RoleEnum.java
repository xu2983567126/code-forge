package com.xly.codeforge.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户角色枚举
 *
 */
@Getter
public enum RoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin"),
    BAN("被封号", "ban");

    private final String text;

    private final String value;

    RoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    private static final Map<String, RoleEnum> VALUE_MAP =
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
    public static RoleEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return VALUE_MAP.get(value);
    }
}
