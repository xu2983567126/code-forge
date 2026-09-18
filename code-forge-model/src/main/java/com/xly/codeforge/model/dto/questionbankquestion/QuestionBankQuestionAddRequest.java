package com.xly.codeforge.model.dto.questionbankquestion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单条「题目加入题单」请求
 *
 * @author xuxu
 */
@Data
public class QuestionBankQuestionAddRequest implements Serializable {

    /**
     * 题单 id
     */
    private Long questionBankId;

    /**
     * 题目 id
     */
    private Long questionId;

    @Serial
    private static final long serialVersionUID = 1L;
}
