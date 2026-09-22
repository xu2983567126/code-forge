package com.xly.codeforge.model.vo;

import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.judge.CodeTemplateGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M7-3 核心代码模式：{@code driverCode} 不落库、也不经 QuestionVO 透出，
 * 运行时由 {@code codeTemplate}（Solution 骨架）经 {@link CodeTemplateGenerator#deriveDriverCode} 派生。
 * 本测试验证两点：
 * <ol>
 *   <li>QuestionVO 已无 driverCode 字段（单一来源是生成器，避免传输一份易失同步的副本）；</li>
 *   <li>生成器是驱动的唯一权威——Solution 骨架派生出含 Main 的驱动，非 Solution 骨架派生为 null。</li>
 * </ol>
 */
class QuestionDriverCodeTest {

    @Test
    void questionVO_no_longer_exposes_driverCode() {
        // 反射确认 VO 上已无 getDriverCode，单一来源是 CodeTemplateGenerator
        assertThrows(NoSuchMethodException.class,
                () -> QuestionVO.class.getMethod("getDriverCode"),
                "QuestionVO 不应再暴露 driverCode");
    }

    @Test
    void generator_is_single_source_for_solution_skeleton() {
        Question q = new Question();
        q.setCodeTemplate("class Solution {\n    public int maxSubArray(int[] nums) {\n        // TODO\n    }\n}\n");
        String driver = CodeTemplateGenerator.deriveDriverCode(q.getCodeTemplate());
        assertNotNull(driver, "Solution 骨架应派生出 driver");
        assertTrue(driver.contains("public class Main"), "driver 必须含 public class Main");
        assertTrue(driver.contains("maxSubArray"), "driver 必须调用题目方法");
    }

    @Test
    void generator_derives_null_for_non_solution_template() {
        // 普通题的 codeTemplate 不是合法 Solution 骨架（或为空），派生 driver 为 null，按普通题回落
        Question q = new Question();
        q.setCodeTemplate("public class Main {}");
        assertNull(CodeTemplateGenerator.deriveDriverCode(q.getCodeTemplate()), "非 Solution 骨架不应派生 driver");
    }
}
