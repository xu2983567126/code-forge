package com.xly.codeforge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题单收藏
 *
 * <p>与 {@link QuestionFavourite} 同构，但收藏对象是<b>题单</b>。
 * 两者分表的原因：{@code question_favourite.question_id} 的语义是「题目 id」，
 * 若混入题单 id，同一个 id 空间会有两套含义 —— 查询、统计、级联删除时
 * 都无法区分「这条记录指的是题还是题单」。</p>
 *
 * <p>硬删除（无 {@code is_delete}）：取消收藏即真删。</p>
 *
 * @TableName question_bank_favourite
 */
@TableName(value = "question_bank_favourite")
@Data
public class QuestionBankFavourite implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题单 id
     */
    private Long bankId;

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

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
