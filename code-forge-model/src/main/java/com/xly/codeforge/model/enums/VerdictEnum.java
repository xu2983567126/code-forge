package com.xly.codeforge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 判题结果枚举（verdict）
 *
 * <p><b>为什么需要这个枚举，而不是直接用 {@link JudgeInfoMessageEnum}</b>：</p>
 * <ul>
 *   <li>{@link JudgeInfoMessageEnum} 的 {@code value} 是<b>给人看的中英混排文案</b>
 *       （如 {@code "Wrong Answer"}、{@code "Time Limit Exceeded"}），带空格、长度不定。
 *       它适合放在 {@code judgeInfo} 这个 JSON 字段里展示，但<b>不适合落库做索引</b>——
 *       {@code question_submit.verdict} 列上有 {@code idx_status_verdict} 索引，
 *       存储值必须稳定、紧凑、可枚举。</li>
 *   <li>本枚举提供稳定的英文 {@code code}（{@code ACCEPTED} / {@code WRONG_ANSWER}），
 *       专用于落库与前端筛选。</li>
 * </ul>
 *
 * <p><b>为什么不建表</b>：判题结果是<b>代码里的行为契约</b>，不是可运营的数据。
 * 新建一张 {@code verdict} 表意味着多一次 JOIN、多一份需要同步的迁移脚本，
 * 而它的取值集合只会随判题策略变化改变 —— 那种场景下改代码本来也是必须的。
 * 故用枚举，不落表。</p>
 *
 * <p><b>与 {@link JudgeInfoMessageEnum} 的映射</b>见 {@link #fromJudgeMessage(String)}。</p>
 *
 * @author xuxu
 */
public enum VerdictEnum {

    /** 通过 */
    ACCEPTED("通过", "ACCEPTED", "success"),

    /** 答案错误 */
    WRONG_ANSWER("答案错误", "WRONG_ANSWER", "danger"),

    /** 编译错误 */
    COMPILE_ERROR("编译错误", "COMPILE_ERROR", "danger"),

    /** 运行时错误 */
    RUNTIME_ERROR("运行时错误", "RUNTIME_ERROR", "danger"),

    /** 超时 */
    TIME_LIMIT_EXCEEDED("运行超时", "TIME_LIMIT_EXCEEDED", "warning"),

    /** 内存超限 */
    MEMORY_LIMIT_EXCEEDED("内存超限", "MEMORY_LIMIT_EXCEEDED", "warning"),

    /** 输出格式错误 */
    PRESENTATION_ERROR("格式错误", "PRESENTATION_ERROR", "warning"),

    /** 危险操作（代码里出现被禁止的调用） */
    DANGEROUS_OPERATION("危险操作", "DANGEROUS_OPERATION", "danger"),

    /** 系统错误（判题链路自身故障，非用户代码问题） */
    SYSTEM_ERROR("系统错误", "SYSTEM_ERROR", "info");

    /** 中文文案，用于前端直接展示 */
    private final String text;

    /** 落库 code，稳定且紧凑 */
    private final String code;

    /** 前端标签颜色语义（success / danger / warning / info），只有 4 种取值 */
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

    /**
     * 根据 code 获取枚举
     *
     * @param code 落库 code
     * @return 匹配的枚举，无匹配返回 null
     */
    public static VerdictEnum getEnumByCode(String code) {
        if (ObjectUtils.isEmpty(code)) {
            return null;
        }
        for (VerdictEnum anEnum : VerdictEnum.values()) {
            if (anEnum.code.equals(code)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 把判题策略产出的 {@link JudgeInfoMessageEnum} 文案，映射为落库用的 verdict 枚举。
     *
     * <p>映射依据是 {@link JudgeInfoMessageEnum#getValue()}（如 {@code "Wrong Answer"}），
     * 而不是 {@code getText()}（如 {@code "答案错误"}）—— 判题策略里
     * {@code DefaultJudgeStrategy} 写入 {@code judgeInfo.message} 时用的正是 value。</p>
     *
     * <p><b>命名不必强求一致</b>：{@link JudgeInfoMessageEnum} 里编译错误叫
     * {@code "Compiling Error"}，本枚举对应项叫 {@code COMPILE_ERROR}（少了 ing）。
     * 这是 OJ 领域的通用写法（各大 OJ 均用 CE 而非 CING），故此处<b>刻意不对齐</b>，仅做语义映射。</p>
     *
     * <p>无法识别的文案一律归入 {@link #SYSTEM_ERROR}：判题结果不可丢，
     * 宁可显示「系统错误」也不能让 verdict 为空 —— 空值会让提交记录在前端筛选中<b>消失</b>。</p>
     *
     * @param judgeMessage 判题信息文案（{@link JudgeInfoMessageEnum} 的 value）
     * @return 对应的 verdict 枚举，永不为 null
     */
    public static VerdictEnum fromJudgeMessage(String judgeMessage) {
        if (ObjectUtils.isEmpty(judgeMessage)) {
            return SYSTEM_ERROR;
        }
        return switch (judgeMessage) {
            case "Accepted" -> ACCEPTED;
            case "Wrong Answer" -> WRONG_ANSWER;
            case "Compiling Error" -> COMPILE_ERROR;
            case "Runtime Error" -> RUNTIME_ERROR;
            case "Time Limit Exceeded" -> TIME_LIMIT_EXCEEDED;
            case "Memory Limit Exceeded" -> MEMORY_LIMIT_EXCEEDED;
            case "Presentation Error" -> PRESENTATION_ERROR;
            case "Dangerous Operation" -> DANGEROUS_OPERATION;
            // 含 "System Error"、以及判题链路异常兜底写入的其它文案
            default -> SYSTEM_ERROR;
        };
    }

    public String getText() {
        return text;
    }

    public String getCode() {
        return code;
    }

    public String getColor() {
        return color;
    }
}
