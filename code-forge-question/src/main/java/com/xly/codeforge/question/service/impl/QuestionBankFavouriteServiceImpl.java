package com.xly.codeforge.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.model.dto.questionbankfavourite.QuestionBankFavouriteRequest;
import com.xly.codeforge.model.entity.QuestionBank;
import com.xly.codeforge.model.entity.QuestionBankFavourite;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionBankVO;
import com.xly.codeforge.question.mapper.QuestionBankFavouriteMapper;
import com.xly.codeforge.question.service.QuestionBankFavouriteService;
import com.xly.codeforge.question.service.QuestionBankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题单收藏服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class QuestionBankFavouriteServiceImpl extends ServiceImpl<QuestionBankFavouriteMapper, QuestionBankFavourite>
        implements QuestionBankFavouriteService {

    /**
     * 分页大小上限（防爬虫）
     */
    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private QuestionBankService questionBankService;

    @Override
    public int toggleFavourite(long bankId, User loginUser) {
        BusinessAssert.isTrue(loginUser != null && loginUser.getId() != null, ErrorCode.NOT_LOGIN_ERROR);
        BusinessAssert.isTrue(bankId > 0, ErrorCode.PARAMS_ERROR);
        // 复用题单服务的读取权限校验：私有题单越权会在这里抛 404，
        // 于是「收藏他人私有题单」这条路自然被堵住，不需要另写一套判断。
        questionBankService.getQuestionBankById(bankId, loginUser);

        Long userId = loginUser.getId();
        QuestionBankFavourite existing = this.getOne(new LambdaQueryWrapper<QuestionBankFavourite>()
                .eq(QuestionBankFavourite::getBankId, bankId)
                .eq(QuestionBankFavourite::getUserId, userId), false);
        if (existing != null) {
            this.removeById(existing.getId());
            return -1;
        }
        QuestionBankFavourite favourite = new QuestionBankFavourite();
        favourite.setBankId(bankId);
        favourite.setUserId(userId);
        try {
            this.save(favourite);
        } catch (DuplicateKeyException e) {
            // 并发双击兜底：记录已存在即视为收藏成功
            log.info("题单 {} 被用户 {} 并发收藏，唯一键兜底", bankId, userId);
        }
        return 1;
    }

    @Override
    public Page<QuestionBankVO> pageMyFavouriteBank(QuestionBankFavouriteRequest queryRequest, User loginUser) {
        BusinessAssert.isTrue(loginUser != null && loginUser.getId() != null, ErrorCode.NOT_LOGIN_ERROR);
        BusinessAssert.notNull(queryRequest, ErrorCode.PARAMS_ERROR);
        long current = Math.max(queryRequest.getCurrent(), 1);
        long size = queryRequest.getPageSize();
        BusinessAssert.isTrue(size <= MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "页大小不能超过 " + MAX_PAGE_SIZE);

        Long userId = loginUser.getId();
        Page<QuestionBankFavourite> favouritePage = this.page(new Page<>(current, size),
                new LambdaQueryWrapper<QuestionBankFavourite>()
                        .select(QuestionBankFavourite::getBankId)
                        .eq(QuestionBankFavourite::getUserId, userId)
                        .orderByDesc(QuestionBankFavourite::getId));
        Page<QuestionBankVO> voPage = new Page<>(favouritePage.getCurrent(), favouritePage.getSize(),
                favouritePage.getTotal());
        if (CollUtil.isEmpty(favouritePage.getRecords())) {
            return voPage;
        }
        List<Long> bankIdList = favouritePage.getRecords().stream()
                .map(QuestionBankFavourite::getBankId)
                .collect(Collectors.toList());

        // 批量取题单实体并按收藏顺序还原（同「我的题目收藏」的处理，见其注释）
        List<QuestionBank> bankList = questionBankService.listByIds(bankIdList);
        if (CollUtil.isEmpty(bankList)) {
            return voPage;
        }
        String searchText = queryRequest.getSearchText();
        Map<Long, QuestionBank> bankMap = bankList.stream()
                .filter(bank -> StringUtils.isBlank(searchText) || StringUtils.contains(bank.getTitle(), searchText))
                .collect(Collectors.toMap(QuestionBank::getId, bank -> bank, (a, b) -> a));

        // 逐个组装 VO —— getQuestionBankVO 内部对每个题单会打 3 次批量查询，
        // 单页最多 20 条，是收藏列表可接受的量级；若分页放大需要改成整页批量组装。
        List<QuestionBankVO> voList = bankIdList.stream()
                .map(bankMap::get)
                .filter(java.util.Objects::nonNull)
                // 收藏列表里的题单必然对当前用户可见（收藏时就校验过），
                // 但题单可能事后被创建者改为私有 —— 此时按「不存在」过滤掉，不给提示
                .filter(bank -> bank.getIsPublic() != null && bank.getIsPublic() == 1
                        || bank.getUserId().equals(userId))
                .map(bank -> questionBankService.getQuestionBankVO(bank, loginUser))
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }
    
}
