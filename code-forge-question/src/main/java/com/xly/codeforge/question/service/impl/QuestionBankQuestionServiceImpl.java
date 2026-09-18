package com.xly.codeforge.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.questionbankquestion.QuestionBankQuestionBulkRequest;
import com.xly.codeforge.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.QuestionBank;
import com.xly.codeforge.model.entity.QuestionBankQuestion;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.QuestionBankQuestionActionEnum;
import com.xly.codeforge.question.mapper.QuestionBankQuestionMapper;
import com.xly.codeforge.question.mapper.QuestionMapper;
import com.xly.codeforge.question.service.QuestionBankQuestionService;
import com.xly.codeforge.question.service.QuestionBankService;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题单-题目关联服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion>
        implements QuestionBankQuestionService {

    /**
     * 单次批量操作的题目数上限
     */
    private static final int MAX_QUESTION_BATCH = 200;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private UserFeignClient userFeignClient;

    @Override
    public void addQuestionToBank(long questionBankId, long questionId, User loginUser) {
        checkBankAuth(questionBankId, loginUser);
        int inserted = addQuestionsSilently(questionBankId, List.of(questionId), loginUser.getId());
        // 已存在不算错误：用户的目标状态已经达成。此处仅记录，不抛异常，
        // 前端凭返回的条数自行决定提示「已添加」还是「该题已在题单中」。
        log.debug("题目 {} 加入题单 {}，新增 {} 条", questionId, questionBankId, inserted);
    }

    @Override
    public void removeQuestionFromBank(long questionBankId, long questionId, User loginUser) {
        checkBankAuth(questionBankId, loginUser);
        int deleted = baseMapper.delete(new LambdaQueryWrapper<QuestionBankQuestion>()
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .eq(QuestionBankQuestion::getQuestionId, questionId));
        // 本来就不在题单里 —— 目标状态已达成，不报错；但返回 0 会影响前端提示，
        // 这里同样按「已达成」放行（幂等删除）。
        log.debug("题目 {} 从题单 {} 移出，影响行数 {}", questionId, questionBankId, deleted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bulkOperateQuestion(QuestionBankQuestionBulkRequest bulkRequest, User loginUser) {
        ThrowUtils.throwIf(bulkRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionBankId = bulkRequest.getQuestionBankId();
        List<Long> questionIdList = bulkRequest.getQuestionIdList();
        ThrowUtils.throwIf(ObjectUtils.isEmpty(questionBankId) || questionBankId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表不能为空");
        ThrowUtils.throwIf(questionIdList.size() > MAX_QUESTION_BATCH, ErrorCode.PARAMS_ERROR,
                "单次最多操作 " + MAX_QUESTION_BATCH + " 道题目");

        // action 走枚举校验：前端拼错（add/plus）时明确报错，而不是静默无操作
        QuestionBankQuestionActionEnum action = QuestionBankQuestionActionEnum.getEnumByValue(bulkRequest.getAction());
        ThrowUtils.throwIf(action == null, ErrorCode.PARAMS_ERROR,
                "action 取值非法，仅支持 ADD / REMOVE");

        checkBankAuth(questionBankId, loginUser);

        // 去重：前端多选时可能重复提交同一个 id，重复会让批量插入踩唯一键
        List<Long> distinctIds = questionIdList.stream().distinct().collect(Collectors.toList());

        if (QuestionBankQuestionActionEnum.ADD.equals(action)) {
            return addQuestionsSilently(questionBankId, distinctIds, loginUser.getId());
        }
        // REMOVE：一条 DELETE ... IN (...) 删完。
        // 这里刻意不学 pandora 的「循环单条删除 + 逐条校验存在性」——
        // 那样既慢，又会在「有一个 id 不存在」时把整批回滚，用户被迫一个个试。
        return baseMapper.delete(new LambdaQueryWrapper<QuestionBankQuestion>()
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, distinctIds));
    }

    @Override
    public int addQuestionsSilently(long questionBankId, List<Long> questionIdList, Long userId) {
        if (CollUtil.isEmpty(questionIdList)) {
            return 0;
        }
        // 题目必须真实存在且未删除：脏 id 写进关联表后，题单会显示「幽灵题」，
        // 点进去 404，且从列表里看不出哪条是坏的。
        List<Question> existQuestions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .select(Question::getId)
                .in(Question::getId, questionIdList));
        Set<Long> existIds = existQuestions.stream().map(Question::getId).collect(Collectors.toSet());
        ThrowUtils.throwIf(existIds.isEmpty(), ErrorCode.PARAMS_ERROR, "所选题目均不存在");

        // 已存在的关联先查出来剔除：直接插会撞唯一键 uk_bank_question 抛异常，
        // 而「重复添加」是用户的正常误操作，不该报 500。
        Set<Long> alreadyInBank = baseMapper.selectList(new LambdaQueryWrapper<QuestionBankQuestion>()
                        .select(QuestionBankQuestion::getQuestionId)
                        .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                        .in(QuestionBankQuestion::getQuestionId, existIds))
                .stream()
                .map(QuestionBankQuestion::getQuestionId)
                .collect(Collectors.toSet());

        List<QuestionBankQuestion> toInsert = new ArrayList<>();
        for (Long questionId : existIds) {
            if (alreadyInBank.contains(questionId)) {
                continue;
            }
            QuestionBankQuestion relation = new QuestionBankQuestion();
            relation.setQuestionBankId(questionBankId);
            relation.setQuestionId(questionId);
            relation.setUserId(userId);
            toInsert.add(relation);
        }
        if (toInsert.isEmpty()) {
            return 0;
        }
        // MP 的 saveBatch 走 ExecutorType.BATCH，比循环 insert 少一次往返/条
        this.saveBatch(toInsert);
        return toInsert.size();
    }

    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest queryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (queryRequest == null) {
            return queryWrapper;
        }
        Long id = queryRequest.getId();
        Long questionBankId = queryRequest.getQuestionBankId();
        Long questionId = queryRequest.getQuestionId();
        Long userId = queryRequest.getUserId();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "question_bank_id", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "question_id", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "user_id", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder == null || sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        return queryWrapper;
    }

    @Override
    public Page<Long> pageQuestionIdsInBank(long questionBankId, long current, long pageSize) {
        ThrowUtils.throwIf(questionBankId <= 0, ErrorCode.PARAMS_ERROR);
        Page<QuestionBankQuestion> relationPage = this.page(new Page<>(current, pageSize),
                new LambdaQueryWrapper<QuestionBankQuestion>()
                        .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                        .orderByDesc(QuestionBankQuestion::getId));
        Page<Long> idPage = new Page<>(relationPage.getCurrent(), relationPage.getSize(), relationPage.getTotal());
        idPage.setRecords(relationPage.getRecords().stream()
                .map(QuestionBankQuestion::getQuestionId)
                .collect(Collectors.toList()));
        return idPage;
    }

    /**
     * 校验当前用户对题单是否有编辑权（本人或管理员）
     *
     * <p>题单不存在、私有题单越权，一律按 404 处理 —— 见
     * {@code QuestionBankService#getQuestionBankById} 的口径说明。</p>
     */
    private void checkBankAuth(long questionBankId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        if (!loginUser.getId().equals(questionBank.getUserId()) && !userFeignClient.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
    }
}
