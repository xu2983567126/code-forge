package com.xly.codeforge.model.vo;

import com.xly.codeforge.model.entity.QuestionBank;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题单视图对象
 *
 * <p>「列表项」与「详情」共用本类，靠字段是否赋值区分：
 * 列表只有基础信息 + {@link #questionCount}；详情额外带题目分页与创建者信息。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionBankVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 题单标题
     */
    private String title;

    /**
     * 题单描述
     */
    private String description;

    /**
     * 题单封面图 URL
     */
    private String picture;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 是否公开：0-私有 1-公开
     */
    private Integer isPublic;

    /**
     * 是否被当前登录用户收藏
     *
     * <p>未登录时恒为 false。列表接口一次性批量查出，避免 N+1。</p>
     */
    private Boolean isFavourited;

    /**
     * 题单内题目总数
     */
    private Long questionCount;

    /**
     * 当前登录用户在该题单内已通过的题目数
     *
     * <p>用于题目列表页的进度环（已通过 / 总数）。未登录时为 0。</p>
     */
    private Long solvedCount;

    /**
     * fork 来源题单 id（NULL = 原创）
     */
    private Long sourceBankId;

    /**
     * 被 fork 次数
     */
    private Integer forkNum;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建者信息（详情接口填充）
     */
    private UserVO userVO;

    // 题单内题目走独立分页接口（GET/POST /question-bank-question/{id}/questions），
    // 详情接口只回元信息与计数 —— 题目数量没有上限，塞进详情会把响应撑爆。

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象转包装类
     *
     * @param questionBank 题单实体
     * @return 视图对象，入参为 null 时返回 null
     */
    public static QuestionBankVO objToVo(QuestionBank questionBank) {
        if (questionBank == null) {
            return null;
        }
        QuestionBankVO questionBankVO = new QuestionBankVO();
        BeanUtils.copyProperties(questionBank, questionBankVO);
        return questionBankVO;
    }
}
