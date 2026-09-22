package com.xly.codeforge.model.vo;

import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuestionAdminVO#objToVo(Question)}：管理侧脱敏反转 —— 答案与全部用例仅在此 VO 出现。
 */
class QuestionAdminVOTest {

    @Test
    void null输入返回null不抛错() {
        assertThat(QuestionAdminVO.objToVo(null)).isNull();
    }

    @Test
    void 完整题目转换含答案与用例() {
        Question question = new Question();
        question.setId(7L);
        question.setTitle("A+B");
        question.setTags("[\"简单\",\"数学\"]");
        question.setAnswer("s = a + b");
        question.setDifficulty("easy");
        question.setJudgeCase("[{\"input\":\"1 2\",\"expectedOutput\":\"3\"},{\"input\":\"10 20\",\"expectedOutput\":\"30\"}]");
        question.setJudgeConfig("{\"timeLimit\":1000,\"memoryLimit\":102400}");
        question.setIsDelete(0);

        QuestionAdminVO vo = QuestionAdminVO.objToVo(question);

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getTitle()).isEqualTo("A+B");
        assertThat(vo.getTags()).containsExactly("简单", "数学");
        // 管理侧专有字段：答案与完整用例（区别于 QuestionVO 的脱敏 examples）
        assertThat(vo.getAnswer()).isEqualTo("s = a + b");
        assertThat(vo.getIsDelete()).isZero();

        List<JudgeCase> judgeCases = vo.getJudgeCase();
        assertThat(judgeCases).hasSize(2);
        assertThat(judgeCases.get(0).getInput()).isEqualTo("1 2");
        assertThat(judgeCases.get(1).getExpectedOutput()).isEqualTo("30");

        JudgeConfig judgeConfig = vo.getJudgeConfig();
        assertThat(judgeConfig).isNotNull();
        assertThat(judgeConfig.getTimeLimit()).isEqualTo(1000L);
        assertThat(judgeConfig.getMemoryLimit()).isEqualTo(102400L);
    }

    @Test
    void judgeCase缺失时保持null而不是空列表() {
        Question question = new Question();
        question.setId(1L);
        question.setJudgeCase(null);

        QuestionAdminVO vo = QuestionAdminVO.objToVo(question);

        assertThat(vo.getJudgeCase()).isNull();
    }

    @Test
    void objToVo保留spjCode与spjLanguage_供SPJ装配() {
        // M7-5 装配层靠 QuestionAdminVO 携带 spjCode/spjLanguage 注入 JudgeContext；
        // 若此处丢失，配置了 SPJ 的题会回落标准比对（历史 bug）。
        Question question = new Question();
        question.setId(9L);
        question.setSpjCode("public class Main {}");
        question.setSpjLanguage("java");

        QuestionAdminVO vo = QuestionAdminVO.objToVo(question);

        assertThat(vo.getSpjCode()).isEqualTo("public class Main {}");
        assertThat(vo.getSpjLanguage()).isEqualTo("java");
    }
}
