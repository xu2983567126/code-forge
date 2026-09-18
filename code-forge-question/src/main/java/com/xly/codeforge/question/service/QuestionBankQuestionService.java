package com.xly.codeforge.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.questionbankquestion.QuestionBankQuestionBulkRequest;
import com.xly.codeforge.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.xly.codeforge.model.entity.QuestionBankQuestion;
import com.xly.codeforge.model.entity.User;

import java.util.List;

/**
 * 题单-题目关联服务
 *
 * @author xuxu
 */
public interface QuestionBankQuestionService extends IService<QuestionBankQuestion> {

    /**
     * 向题单添加单道题目
     *
     * @param questionBankId 题单 id
     * @param questionId     题目 id
     * @param loginUser      当前登录用户（须为题单创建者或管理员）
     */
    void addQuestionToBank(long questionBankId, long questionId, User loginUser);

    /**
     * 从题单移出单道题目
     *
     * @param questionBankId 题单 id
     * @param questionId     题目 id
     * @param loginUser      当前登录用户（须为题单创建者或管理员）
     */
    void removeQuestionFromBank(long questionBankId, long questionId, User loginUser);

    /**
     * 批量添加 / 移出题目
     *
     * <p>单条 SQL 完成，不做循环。移出不存在的关联<b>不算失败</b> ——
     * 用户的目标状态是「这批题不在题单里」，本来就不在等于已经达成。</p>
     *
     * @param bulkRequest 批量请求（{@code action} 取 ADD / REMOVE）
     * @param loginUser   当前登录用户
     * @return 实际影响的行数
     */
    int bulkOperateQuestion(QuestionBankQuestionBulkRequest bulkRequest, User loginUser);

    /**
     * 批量添加题目（内部复用，不校验权限）
     *
     * <p>供 {@code QuestionBankService#forkQuestionBank} 与建题单时带初始题目使用，
     * 调用方已自行完成权限校验。</p>
     *
     * @param questionBankId 题单 id
     * @param questionIdList 题目 id 列表
     * @param userId         操作人 id
     * @return 实际插入条数（已存在的会被唯一键或预检跳过）
     */
    int addQuestionsSilently(long questionBankId, List<Long> questionIdList, Long userId);

    /**
     * 获取查询条件
     *
     * @param queryRequest 查询请求
     * @return 查询包装类
     */
    QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest queryRequest);

    /**
     * 分页获取题单内的题目 id 列表
     *
     * @param questionBankId 题单 id
     * @param current        当前页
     * @param pageSize       页大小
     * @return 题目 id 分页
     */
    Page<Long> pageQuestionIdsInBank(long questionBankId, long current, long pageSize);
}
