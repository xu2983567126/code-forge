package com.xly.codeforge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题单（题目集合）
 *
 * <p>用户可以把自己的题单公开或设为私有。私有题单除创建者（及管理员）外
 * 一律视为「不存在」——对外返回 404 而非 403，避免 id 探测。</p>
 *
 * @TableName question_bank
 */
@TableName(value = "question_bank")
@Data
public class QuestionBank implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     *
     * <p>用 {@link Integer} 而非 {@code Boolean}：DB 列是 {@code tinyint}，
     * 且 MyBatis-Plus 对 Boolean ↔ tinyint 的映射在不同版本行为不一致，显式用 Integer 更稳。</p>
     */
    private Integer isPublic;

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
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
