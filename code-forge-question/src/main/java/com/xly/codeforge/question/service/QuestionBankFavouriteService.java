package com.xly.codeforge.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.questionbankfavourite.QuestionBankFavouriteRequest;
import com.xly.codeforge.model.entity.QuestionBankFavourite;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionBankVO;

/**
 * 题单收藏服务
 *
 * @author xuxu
 */
public interface QuestionBankFavouriteService extends IService<QuestionBankFavourite> {

    /**
     * 收藏 / 取消收藏题单（切换式）
     *
     * <p>只有「可见的题单」才能被收藏 —— 私有题单对无权限者等于不存在。</p>
     *
     * @param bankId    题单 id
     * @param loginUser 当前登录用户
     * @return 1-已收藏 -1-已取消
     */
    int toggleFavourite(long bankId, User loginUser);

    /**
     * 分页获取当前用户收藏的题单
     *
     * @param queryRequest 查询请求（关键词 / 分页）
     * @param loginUser    当前登录用户
     * @return 题单 VO 分页
     */
    Page<QuestionBankVO> pageMyFavouriteBank(QuestionBankFavouriteRequest queryRequest, User loginUser);
}
