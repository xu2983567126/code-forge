package com.xly.codeforge.model.vo;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.entity.Submission;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目
 *
 * @TableName question
 */
@TableName(value = "question")
@Data
public class SubmissionVO implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 判题信息（json 对象）
     */
    private JudgeInfo judgeInfo;

    /**
     * 判题状态（0 - 待判题、1 - 判题中、2 - 成功、3 - 失败）
     */
    private Integer status;

    /**
     * 判题结果（verdict）
     *
     * <p>取值见 {@link com.xly.codeforge.model.enums.VerdictEnum}。冗余自 {@link #judgeInfo}
     * 的 {@code message} 字段，便于前端直接筛选 / 上色，无需再解析 JSON。</p>
     */
    private String verdict;

    /**
     * 题目 id
     */
    private Long questionId;

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
     * 提交者
     */
    private UserVO userVO;

    /**
     * 题目信息
     */
    private QuestionVO questionVO;

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * 包装类转对象
     *
     * @param submissionVO
     * @return
     */
    public static Submission voToObj(SubmissionVO submissionVO) {
        if (submissionVO == null) {
            return null;
        }
        Submission submission = new Submission();
        BeanUtils.copyProperties(submissionVO, submission);
        JudgeInfo judgeInfoObj = submissionVO.getJudgeInfo();
        if (judgeInfoObj != null) {
            submission.setJudgeInfo(JSONUtil.toJsonStr(judgeInfoObj));
        }
        return submission;
    }

    /**
     * 对象转包装类
     *
     * @param submission
     * @return
     */
    public static SubmissionVO objToVo(Submission submission) {
        if (submission == null) {
            return null;
        }
        SubmissionVO submissionVO = new SubmissionVO();
        BeanUtils.copyProperties(submission, submissionVO);
        String judgeInfoStr = submission.getJudgeInfo();
        submissionVO.setJudgeInfo(JSONUtil.toBean(judgeInfoStr, JudgeInfo.class));
        return submissionVO;
    }
}