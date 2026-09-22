package com.xly.codeforge.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 判题结论枚举（verdict）—— 判题结果的<b>唯一真相源</b>。
 *
 * <p>一次判题的结论（AC / WA / TLE / MLE / RE / CE / PE / 危险操作 / 系统错误）只由本枚举描述，
 * 不再像早期那样分两套枚举（落库的 code + 展示的英文文案）互桥。</p>
 *
 * <p>三个字段各司其职：</p>
 * <ul>
 *   <li>{@code code}：稳定、紧凑的英文码（{@code ACCEPTED} / {@code WRONG_ANSWER}），落库到
 *       {@code submission.verdict} 列并接受 {@code idx_status_verdict} 索引；同时也作为
 *       {@code JudgeInfo.message} 与逐用例 {@code JudgeCaseResult.status} 的值，前后端同码。</li>
 *   <li>{@code text}：中文展示文案，供后端直接拼接口响应。</li>
 *   <li>{@code color}：前端标签语义色（success / danger / warning / info），共 4 种取值。</li>
 * </ul>
 *
 * <p><b>为什么不建表</b>：判题结果是<b>代码里的行为契约</b>，不是可运营的数据。
 * 取值集合只会随判题策略变化改变 —— 那种场景下改代码本来也是必须的，故用枚举，不落表。</p>
 *
 * @author xuxu
 */
@Getter
public enum VerdictEnum {

    /**
     * 通过
     */
    ACCEPTED("通过", "ACCEPTED", "success"),

    /**
     * 答案错误
     */
    WRONG_ANSWER("答案错误", "WRONG_ANSWER", "danger"),

    /**
     * 编译错误
     */
    COMPILE_ERROR("编译错误", "COMPILE_ERROR", "danger"),

    /**
     * 运行时错误
     */
    RUNTIME_ERROR("运行时错误", "RUNTIME_ERROR", "danger"),

    /**
     * 超时
     */
    TIME_LIMIT_EXCEEDED("运行超时", "TIME_LIMIT_EXCEEDED", "warning"),

    /**
     * 内存超限
     */
    MEMORY_LIMIT_EXCEEDED("内存超限", "MEMORY_LIMIT_EXCEEDED", "warning"),

    /**
     * 输出格式错误
     */
    PRESENTATION_ERROR("格式错误", "PRESENTATION_ERROR", "warning"),

    /**
     * 危险操作（代码里出现被禁止的调用）
     */
    DANGEROUS_OPERATION("危险操作", "DANGEROUS_OPERATION", "danger"),

    /**
     * 系统错误（判题链路自身故障，非用户代码问题）
     */
    SYSTEM_ERROR("系统错误", "SYSTEM_ERROR", "info");

    /**
     * 中文文案，用于前端直接展示
     */
    private final String text;

    /**
     * 落库 code，稳定且紧凑
     */
    private final String code;

    /**
     * 前端标签颜色语义（success / danger / warning / info），只有 4 种取值
     */
    private final String color;

    VerdictEnum(String text, String code, String color) {
        this.text = text;
        this.code = code;
        this.color = color;
    }

    /**
     * 获取全部 code 列表（供前端下拉框 / 参数校验使用）
     *
     * @return code 列表
     */
    public static List<String> getCodes() {
        return Arrays.stream(values())
            .map(VerdictEnum::getCode)
            .collect(Collectors.toList());
    }


    private static final Map<String, VerdictEnum> VALUE_MAP =
        Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                e -> e.code,
                e -> e,
                (a, _) -> {
                    throw new IllegalStateException("重复的 code: " + a.code);
                }));

    /**
     * 根据 code 获取枚举
     */
    public static VerdictEnum getEnumByCode(String code) {
        if (ObjectUtils.isEmpty(code)) {
            return null;
        }
        return VALUE_MAP.get(code);
    }
}
