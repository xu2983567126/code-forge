package com.xly.codeforge.judge.contract;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.model.dto.question.JudgeCase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 题目用例 wire key 契约测试。
 *
 * <p>锁的是「{@link JudgeCase} 对外 JSON 的字段名」这件事本身，而不是某个业务的判题规则。
 * 之所以要专门锁：题目用例存的是 **JSON 文本列**（{@code question.judge_case}），
 * 字段名对不上时 Hutool 是<b>静默丢弃</b>，不报错、不告警 —— 表现为
 * {@code expectedOutput} 恒为 null → 正式判题落进中性态 → 落库
 * 「status=2（成功）+ verdict=SYSTEM_ERROR」。这类断裂只能靠契约测试拦。</p>
 *
 * <p>改动本测试的时机：确实要改 wire key 时 —— 那时必须同时提供数据回填脚本
 * （见 {@code sql/migrations/2026-09-21-fix-judge-case-wire-key.sql}），
 * 两者一起改，别只改一边。</p>
 */
class JudgeCaseWireContractTest {

    /**
     * 字段集合即 wire key 集合：任何增删字段都会让本测试失败，迫使改动者显式确认"这是契约变更"。
     */
    @Test
    void JudgeCase的字段集合即对外契约() {
        Set<String> fieldNames = Arrays.stream(JudgeCase.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .as("JudgeCase 是 question.judge_case 的 JSON 契约，字段名即 wire key")
                .containsExactlyInAnyOrder("input", "expectedOutput");
    }

    /**
     * 序列化出来的 key 必须是 {@code expectedOutput}，绝不能是旧名 {@code output}
     * （{@code output} 在沙箱/返回侧表示"用户实际输出"，两者同名反向是历史坑）。
     */
    @Test
    void 序列化出的key是expectedOutput而非output() {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInput("1 2");
        judgeCase.setExpectedOutput("3");

        String json = JSONUtil.toJsonStr(judgeCase);

        assertThat(json).contains("\"expectedOutput\"");
        assertThat(json).doesNotContain("\"output\":");
    }

    /**
     * 正向：题库写入侧产出的 JSON 必须能被判题侧读回非空期望输出。
     */
    @Test
    void 新格式JSON能解析出非空期望输出() {
        List<JudgeCase> cases = JSONUtil.toList(
                "[{\"input\": \"[1,2]\", \"expectedOutput\": \"3\"}]", JudgeCase.class);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getInput()).isEqualTo("[1,2]");
        assertThat(cases.get(0).getExpectedOutput()).isEqualTo("3");
    }

    /**
     * 反向（把已知陷阱钉住）：旧格式（key=output）解析后期望输出为 {@code null}，且**不抛异常**。
     *
     * <p>这条断言的价值在于记录"静默丢弃"这一事实：一旦将来有人给 JudgeCase 加了别名兼容、
     * 或换了反序列化库改变了未知字段的处理策略，本测试会红，提醒重新评估
     * 「改名是否必须配数据回填」这个结论（当前结论：必须）。</p>
     */
    @Test
    void 旧格式JSON被静默丢弃期望输出为零值() {
        List<JudgeCase> cases = JSONUtil.toList(
                "[{\"input\": \"[1,2]\", \"output\": \"3\"}]", JudgeCase.class);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getInput()).isEqualTo("[1,2]");
        assertThat(cases.get(0).getExpectedOutput())
                .as("旧 key 不会报错，只会静默变成 null —— 这正是判题落进中性态的根因")
                .isNull();
    }
}
