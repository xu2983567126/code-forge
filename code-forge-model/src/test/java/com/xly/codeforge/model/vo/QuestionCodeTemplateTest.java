package com.xly.codeforge.model.vo;

import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M7-3 验证：{@code codeTemplate} 经 QuestionVO / QuestionAdminVO 的
 * voToObj / objToVo 往返一致（BeanUtils 自动拷字段，无需手写）。
 */
class QuestionCodeTemplateTest {

    @Test
    void questionVO_codeTemplate_roundTrip() {
        QuestionVO vo = new QuestionVO();
        vo.setTitle("add two numbers");
        vo.setTags(List.of("default"));
        vo.setJudgeConfig(new JudgeConfig());
        vo.setCodeTemplate("public int add(int a, int b) {}");

        Question question = QuestionVO.voToObj(vo);
        assertEquals("public int add(int a, int b) {}", question.getCodeTemplate());

        QuestionVO back = QuestionVO.objToVo(question);
        assertEquals("public int add(int a, int b) {}", back.getCodeTemplate());
    }

    @Test
    void questionAdminVO_codeTemplate_inherited() {
        QuestionAdminVO admin = new QuestionAdminVO();
        admin.setCodeTemplate("template-x");

        Question question = QuestionAdminVO.voToObj(admin);
        assertEquals("template-x", question.getCodeTemplate());

        QuestionAdminVO back = QuestionAdminVO.objToVo(question);
        assertEquals("template-x", back.getCodeTemplate());
    }
}
