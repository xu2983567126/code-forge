package com.xly.codeforge.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xly.codeforge.model.entity.QuestionBankQuestion;

/**
 * 题单-题目关联 Mapper
 *
 * <p>无需自定义 SQL：批量增删由 Service 用 {@code saveBatch} / {@code remove} 完成，
 * 进度统计挂在 {@link QuestionBankMapper}（那边有跨表 JOIN）。</p>
 *
 * @author xuxu
 */
public interface QuestionBankQuestionMapper extends BaseMapper<QuestionBankQuestion> {
}
