package com.xly.codeforge.model.vo;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.entity.Question;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionAdminVO extends QuestionVO {
    private String answer;                // 题目答案（普通字符串）
    private List<JudgeCase> judgeCase;    // 判题用例（转换后的对象列表）
    private Integer isDelete;             // 逻辑删除标志

    /**
     * QuestionAdminVO → Question 实体
     * 复用父类对 tags、judgeConfig 的 JSON 转换，再处理本类额外字段
     */
    public static Question voToObj(QuestionAdminVO questionAdminVO) {
        if (questionAdminVO == null) {
            return null;
        }
        // 1. 调用父类转换，得到包含基本字段及 tags、judgeConfig（已转 JSON）的 Question 对象
        Question question = QuestionVO.voToObj(questionAdminVO);
        // 2. 处理额外字段
        // answer：直接赋值（普通字符串）
        question.setAnswer(questionAdminVO.getAnswer());
        // judgeCase：List<JudgeCase> → JSON 字符串
        if (questionAdminVO.getJudgeCase() != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(questionAdminVO.getJudgeCase()));
        }
        // isDelete：直接赋值
        question.setIsDelete(questionAdminVO.getIsDelete());
        return question;
    }

    /**
     * Question 实体 → QuestionAdminVO
     * 复用父类对 tags、judgeConfig 的反序列化，再处理本类额外字段
     */
    public static QuestionAdminVO objToVo(Question question) {
        if (question == null) {
            return null;
        }
        // 1. 调用父类转换，得到包含基本字段及转换后 tags、judgeConfig 的 QuestionVO 对象
        QuestionVO questionVO = QuestionVO.objToVo(question);
        // 2. 创建 QuestionAdminVO 并复制父类所有属性（因为子类不能直接强转）
        QuestionAdminVO adminVO = new QuestionAdminVO();
        BeanUtils.copyProperties(questionVO, adminVO);
        // 3. 处理额外字段
        adminVO.setAnswer(question.getAnswer());
        // judgeCase：JSON 字符串 → List<JudgeCase>
        String judgeCaseStr = question.getJudgeCase();
        if (judgeCaseStr != null) {
            adminVO.setJudgeCase(JSONUtil.toList(judgeCaseStr, JudgeCase.class));
        }
        adminVO.setIsDelete(question.getIsDelete());
        return adminVO;
    }
}