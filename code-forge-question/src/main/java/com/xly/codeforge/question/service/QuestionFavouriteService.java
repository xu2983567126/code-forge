package com.xly.codeforge.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.questionfavourite.QuestionFavouriteQueryRequest;
import com.xly.codeforge.model.entity.QuestionFavourite;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionVO;

/**
 * 题目收藏服务
 *
 * @author xuxu
 */
public interface QuestionFavouriteService extends IService<QuestionFavourite> {

    /**
     * 收藏 / 取消收藏（切换式）
     *
     * <p>同一路径下按当前状态取反：已收藏则删、未收藏则插。
     * 返回 {@code 1} 表示本次是「收藏」，{@code -1} 表示本次是「取消收藏」，
     * 前端据此切换图标与文案，无需再查一次状态。</p>
     *
     * <p><b>并发说明</b>：两个请求同时打进来可能都读到「未收藏」而双双插入，
     * 此时由 {@code uk_question_user} 唯一键兜底 —— 后一条抛重复键异常。
     * 不额外加分布式锁：收藏是低频操作，为此引入锁的复杂度高于收益。</p>
     *
     * @param questionId 题目 id
     * @param loginUser  当前登录用户
     * @return 1-已收藏 -1-已取消
     */
    int toggleFavourite(long questionId, User loginUser);

    /**
     * 分页获取当前用户收藏的题目
     *
     * @param queryRequest 查询请求（关键词 / 难度 / 分页）
     * @param loginUser    当前登录用户
     * @return 题目 VO 分页
     */
    Page<QuestionVO> pageMyFavouriteQuestion(QuestionFavouriteQueryRequest queryRequest, User loginUser);

    /**
     * 查询当前用户是否已收藏某题
     *
     * @param questionId 题目 id
     * @param userId     用户 id，为 null 时返回 false
     * @return true 表示已收藏
     */
    boolean isFavourited(long questionId, Long userId);
}
