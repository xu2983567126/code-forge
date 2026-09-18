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
 * 题单-题目关联
 *
 * <p><b>硬删除</b>（无 {@code is_delete}）：把题目移出题单即真删。
 * 这样 {@code uk_bank_question} 唯一键可以直接复用，不需要「逻辑删除 + 唯一键」
 * 那套绕来绕去的写法（逻辑删除下，删除过的记录仍占用唯一键，会导致无法重新添加）。</p>
 *
 * @TableName question_bank_question
 */
@TableName(value = "question_bank_question")
@Data
public class QuestionBankQuestion implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题单 id
     */
    private Long questionBankId;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 操作（添加）用户 id
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
