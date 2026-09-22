package com.xly.codeforge.model.vo;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.entity.Question;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目（视图对象）
 *
 * <p>这是 VO 不是实体：不带任何 MyBatis-Plus 注解。{@code tags} / {@code judgeConfig} /
 * {@code examples} 由实体的 JSON 文本列解析而来；{@code submitNum} / {@code acceptedNum}
 * 由提交域实时统计回填，不读实体上那两个已废弃的计数列。</p>
 */
@Data
public class QuestionVO implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表（json 数组）
     */
    private List<String> tags;

    /**
     * 难度：简单/中等/困难
     */
    private String difficulty;

    /**
     * 题目提交数
     */
    private Integer submitNum;

    /**
     * 题目通过数
     */
    private Integer acceptedNum;

    /**
     * 判题配置（json 对象）
     */
    private JudgeConfig judgeConfig;

    /**
     * 判题代码模板（编辑器预置骨架，用户可见）
     */
    private String codeTemplate;

    /**
     * 样例用例数上限。题目页只展示样例，judge_case 里其余用例保持隐藏 ——
     * 全量透出会让做题者"对着用例编程"，判题失去意义。
     */
    public static final int EXAMPLE_LIMIT = 2;

    /**
     * 样例用例（judgeCase 的前 EXAMPLE_LIMIT 条）
     */
    private List<JudgeCase> examples;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建者的信息
     */
    private UserVO userVO;

    private static final long serialVersionUID = 1L;


    /**
     * 包装类转对象
     *
     * @param questionVO
     * @return
     */
    public static Question voToObj(QuestionVO questionVO) {
        if (questionVO == null) {
            return null;
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionVO, question);
        List<String> tagList = questionVO.getTags();
        if (tagList != null) {
            question.setTags(JSONUtil.toJsonStr(tagList));
        }
        JudgeConfig judgeConfigVo = questionVO.getJudgeConfig();
        if (judgeConfigVo != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfigVo));
        }
        return question;
    }

    /**
     * 对象转包装类
     *
     * @param question
     * @return
     */
    public static QuestionVO objToVo(Question question) {
        if (question == null) {
            return null;
        }
        QuestionVO questionVO = new QuestionVO();
        BeanUtils.copyProperties(question, questionVO);
        List<String> tagList = JSONUtil.toList(question.getTags(), String.class);
        questionVO.setTags(tagList);
        String judgeConfigStr = question.getJudgeConfig();
        questionVO.setJudgeConfig(JSONUtil.toBean(judgeConfigStr, JudgeConfig.class));
        // 样例：judgeCase 的前 EXAMPLE_LIMIT 条；JSON 非法时置空，不影响详情主流程
        String judgeCaseStr = question.getJudgeCase();
        if (judgeCaseStr != null && !judgeCaseStr.isBlank()) {
            try {
                List<JudgeCase> judgeCases = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
                questionVO.setExamples(
                        judgeCases.subList(0, Math.min(EXAMPLE_LIMIT, judgeCases.size())));
            } catch (Exception e) {
                questionVO.setExamples(null);
            }
        }
        return questionVO;
    }
}