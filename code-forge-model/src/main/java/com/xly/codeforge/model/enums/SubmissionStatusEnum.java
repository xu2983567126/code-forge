package com.xly.codeforge.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目提交流程状态枚举（流程位）：WAITING(0)→RUNNING(1)→SUCCEED(2)/FAILED(3)。
 * <p>只描述「提交处在什么阶段」，不含判题结论；判题结论见 {@link VerdictEnum}（结果位，落在 judge_info 列与 verdict 列）。
 * 两枚举正交，互不直接映射。</p>
 *
 */
@Getter
public enum SubmissionStatusEnum {

    WAITING("等待中", 0),
    RUNNING("判题中", 1),
    SUCCEED("通过", 2),
    FAILED("未通过", 3);

    private final String text;

    private final Integer value;

    SubmissionStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    private static final Map<Integer, SubmissionStatusEnum> VALUE_MAP =
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
    public static SubmissionStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return VALUE_MAP.get(value);
    }
}
