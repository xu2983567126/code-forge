package com.xly.codeforge.model.dto.questionbankquestion;

import com.xly.codeforge.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询题单-题目关联请求
 *
 * @author xuxu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionBankQuestionQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
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
     * 操作用户 id
     */
    private Long userId;

    @Serial
    private static final long serialVersionUID = 1L;
}
