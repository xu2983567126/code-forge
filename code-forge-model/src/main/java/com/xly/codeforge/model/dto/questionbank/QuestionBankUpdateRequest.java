package com.xly.codeforge.model.dto.questionbank;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新题单请求
 *
 * <p>用于 {@code PATCH /question-bank/{id}}。与 {@link QuestionBankAddRequest} 的差别：
 * 带 {@code id}，且<b>不</b>带 {@code questionIdList} —— 改题单元信息与改题目组成
 * 是两件事，混在一起会让「只想改标题」的请求意外触发题目增删。</p>
 *
 * <p>权限：管理员可改任意题单；普通用户只能改自己创建的（由 Service 校验）。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionBankUpdateRequest implements Serializable {

    /**
     * 题单 id
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
     * 是否公开：0-私有 1-公开
     */
    private Integer isPublic;

    @Serial
    private static final long serialVersionUID = 1L;
}
