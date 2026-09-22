package com.xly.codeforge.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.model.dto.questionfavourite.QuestionFavouriteQueryRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.QuestionFavourite;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.question.mapper.QuestionFavouriteMapper;
import com.xly.codeforge.question.mapper.QuestionMapper;
import com.xly.codeforge.question.service.QuestionFavouriteService;
import com.xly.codeforge.question.service.QuestionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目收藏服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class QuestionFavouriteServiceImpl extends ServiceImpl<QuestionFavouriteMapper, QuestionFavourite>
        implements QuestionFavouriteService {

    /**
     * 分页大小上限（防爬虫）
     */
    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionService questionService;

    @Override
    public int toggleFavourite(long questionId, User loginUser) {
        BusinessAssert.isTrue(loginUser != null && loginUser.getId() != null, ErrorCode.NOT_LOGIN_ERROR);
        BusinessAssert.isTrue(questionId > 0, ErrorCode.PARAMS_ERROR);
        // 题目必须存在且未删除，否则会收藏到一个点进去 404 的幽灵条目
        Question question = questionMapper.selectById(questionId);
        BusinessAssert.notNull(question, ErrorCode.NOT_FOUND_ERROR);

        Long userId = loginUser.getId();
        QuestionFavourite existing = this.getOne(new LambdaQueryWrapper<QuestionFavourite>()
                .eq(QuestionFavourite::getQuestionId, questionId)
                .eq(QuestionFavourite::getUserId, userId), false);
        if (existing != null) {
            this.removeById(existing.getId());
            return -1;
        }
        QuestionFavourite favourite = new QuestionFavourite();
        favourite.setQuestionId(questionId);
        favourite.setUserId(userId);
        try {
            this.save(favourite);
        } catch (DuplicateKeyException e) {
            // 并发双击：两个请求都判为「未收藏」，后到的撞唯一键。
            // 此时记录已经存在，语义上等同于收藏成功，不向上抛 500。
            log.info("题目 {} 被用户 {} 并发收藏，唯一键兜底", questionId, userId);
        }
        return 1;
    }

    @Override
    public Page<QuestionVO> pageMyFavouriteQuestion(QuestionFavouriteQueryRequest queryRequest, User loginUser) {
        BusinessAssert.isTrue(loginUser != null && loginUser.getId() != null, ErrorCode.NOT_LOGIN_ERROR);
        BusinessAssert.notNull(queryRequest, ErrorCode.PARAMS_ERROR);
        long current = Math.max(queryRequest.getCurrent(), 1);
        long size = queryRequest.getPageSize();
        BusinessAssert.isTrue(size <= MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "页大小不能超过 " + MAX_PAGE_SIZE);

        Long userId = loginUser.getId();
        // 第一步：分页查收藏记录（只取 question_id，避免把整行拉回来）
        Page<QuestionFavourite> favouritePage = this.page(new Page<>(current, size),
                new LambdaQueryWrapper<QuestionFavourite>()
                        .select(QuestionFavourite::getQuestionId)
                        .eq(QuestionFavourite::getUserId, userId)
                        .orderByDesc(QuestionFavourite::getId));
        Page<QuestionVO> voPage = new Page<>(favouritePage.getCurrent(), favouritePage.getSize(),
                favouritePage.getTotal());
        if (CollUtil.isEmpty(favouritePage.getRecords())) {
            return voPage;
        }
        List<Long> questionIdList = favouritePage.getRecords().stream()
                .map(QuestionFavourite::getQuestionId)
                .collect(Collectors.toList());

        // 第二步：按 id 批量取题目，再按收藏顺序还原。
        // 不能直接 in + orderBy：那样得到的是题目表顺序，与「按收藏时间倒序」的语义不符。
        // 关键词 / 难度筛选在这一步做 —— 放在收藏表查会把「筛掉后的分页」搞乱。
        // 代价是「筛掉后单页可能不满 size」，对收藏这种小数据量场景可接受；
        // 若将来收藏量到几千条，应改成收藏表 JOIN 题目表一条 SQL 分页。
        String searchText = queryRequest.getSearchText();
        String difficulty = queryRequest.getDifficulty();
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIdList);
        if (StringUtils.isNotBlank(searchText)) {
            questionWrapper.like(Question::getTitle, searchText);
        }
        if (StringUtils.isNotBlank(difficulty)) {
            questionWrapper.eq(Question::getDifficulty, difficulty);
        }
        List<Question> questionList = questionMapper.selectList(questionWrapper);
        if (CollUtil.isEmpty(questionList)) {
            return voPage;
        }
        java.util.Map<Long, Question> questionMap = questionList.stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        List<QuestionVO> voList = questionIdList.stream()
                .map(questionMap::get)
                .filter(java.util.Objects::nonNull)
                .map(question -> {
                    QuestionVO vo = questionService.getQuestionVO(question, loginUser);
                    // 能出现在「我的收藏」里的题，对当前用户必然已收藏；显式回填避免前端再查一次
                    vo.setFavourNum(question.getFavourNum());
                    return vo;
                })
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public boolean isFavourited(long questionId, Long userId) {
        if (questionId <= 0 || userId == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<QuestionFavourite>()
                .eq(QuestionFavourite::getQuestionId, questionId)
                .eq(QuestionFavourite::getUserId, userId)) > 0;
    }

    /**
     * 批量查询「某用户已收藏的题目 id」（供题目列表批量标注收藏状态）
     *
     * @param questionIds 题目 id 集合
     * @param userId      用户 id
     * @return 已收藏的题目 id 集合；任一参数为空时返回空集合
     */
    public Set<Long> listFavouritedQuestionIds(List<Long> questionIds, Long userId) {
        if (CollUtil.isEmpty(questionIds) || userId == null) {
            return Collections.emptySet();
        }
        return this.list(new LambdaQueryWrapper<QuestionFavourite>()
                        .select(QuestionFavourite::getQuestionId)
                        .in(QuestionFavourite::getQuestionId, questionIds)
                        .eq(QuestionFavourite::getUserId, userId))
                .stream()
                .map(QuestionFavourite::getQuestionId)
                .collect(Collectors.toSet());
    }
}
