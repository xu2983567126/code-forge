package com.xly.codeforge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 判题输出比对模式。
 *
 * <p>决定「用户输出与标准答案算不算一致」的口径。挂在 {@code JudgeConfig.compareMode} 上，
 * 由题目配置指定；缺省 {@link #STANDARD}。</p>
 *
 * <ul>
 *   <li>{@link #STANDARD} 标准：忽略首尾空白、逐行去行尾空白、忽略末尾空行与换行符差异；
 *       仅<b>内部</b>空白不同时判为 {@code PRESENTATION_ERROR}。</li>
 *   <li>{@link #STRICT} 严格：仅归一化换行符（{@code \r\n → \n}），整串精确比较。</li>
 *   <li>{@link #FLOAT} 浮点：按 epsilon 容差逐数值比较（自研，解决 {@code 0.1+0.2} 精度问题）。</li>
 *   <li>{@link #SPJ} 特判：由题目自带判定程序决定，逻辑留待 M4；M2 不实现执行。</li>
 * </ul>
 */
public enum CompareMode {

    /**
     * 标准比对（默认）
     */
    STANDARD,

    /**
     * 严格比对
     */
    STRICT,

    /**
     * 浮点容差比对
     */
    FLOAT,

    /**
     * 特判（M4 实现）
     */
    SPJ;

    private static final Map<String, CompareMode> VALUE_MAP =
        Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                e -> e.name().toLowerCase(),
                e -> e,
                (a, _) -> {
                    throw new IllegalStateException("重复的 name: " + a.name().toLowerCase());
                }));

    /**
     * 从配置字符串解析，无法识别或为空一律回落 {@link #STANDARD}（不抛异常，保证存量题可判）。
     *
     * @param code 配置值（大小写不敏感）
     * @return 对应的比对模式，永不为 null
     */
    public static CompareMode fromCode(String code) {
        if (ObjectUtils.isEmpty(code)) {
            return STANDARD;
        }
        return VALUE_MAP.getOrDefault(code.toLowerCase(), STANDARD);
    }
}
