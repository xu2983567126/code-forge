package com.xly.codeforge.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.common.BaseResponse;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.common.ResultUtils;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.model.dto.questionbankfavourite.QuestionBankFavouriteRequest;
import com.xly.codeforge.model.dto.questionfavourite.QuestionFavouriteQueryRequest;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionBankVO;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.question.service.QuestionBankFavouriteService;
import com.xly.codeforge.question.service.QuestionFavouriteService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口
 *
 * <p>题目收藏与题单收藏合并到一个 Controller：两者是同一类动作的两个对象，
 * 路径上通过子资源名区分（{@code /question-favourite} / {@code /question-bank-favourite}），
 * 前端只需理解一套「POST 收藏、DELETE 取消」的语义。</p>
 *
 * <p>注意：内部实现是<b>切换式</b>（toggle），POST/DELETE 都调同一个方法 ——
 * 重复点 POST 不会插两条，由唯一键兜底。之所以仍提供两个方法，
 * 是为了让前端语义清晰（明确表达「我要收藏」/「我要取消」），而不是替前端猜状态。</p>
 *
 * @author xuxu
 */
@RestController
@RequestMapping
@Slf4j
public class FavouriteController {

    @Resource
    private QuestionFavouriteService questionFavouriteService;

    @Resource
    private QuestionBankFavouriteService questionBankFavouriteService;

    @Resource
    private UserFeignClient userFeignClient;

    // region 题目收藏

    /**
     * 收藏题目
     *
     * <p>{@code POST /question-favourite/{questionId}}</p>
     *
     * @return 1-已收藏 -1-已取消（切换式语义，见类注释）
     */
    @PostMapping("/question-favourite/{questionId}")
    public BaseResponse<Integer> favouriteQuestion(@PathVariable("questionId") long questionId,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionFavouriteService.toggleFavourite(questionId, loginUser));
    }

    /**
     * 取消收藏题目
     *
     * <p>{@code DELETE /question-favourite/{questionId}}</p>
     */
    @DeleteMapping("/question-favourite/{questionId}")
    public BaseResponse<Integer> unfavouriteQuestion(@PathVariable("questionId") long questionId,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionFavouriteService.toggleFavourite(questionId, loginUser));
    }

    /**
     * 分页获取我的题目收藏
     *
     * <p>{@code POST /question-favourite/my/list/page/vo}</p>
     */
    @PostMapping("/question-favourite/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyFavouriteQuestion(
            @RequestBody QuestionFavouriteQueryRequest queryRequest, HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionFavouriteService.pageMyFavouriteQuestion(queryRequest, loginUser));
    }

    // endregion

    // region 题单收藏

    /**
     * 收藏题单
     *
     * <p>{@code POST /question-bank-favourite/{bankId}}</p>
     */
    @PostMapping("/question-bank-favourite/{bankId}")
    public BaseResponse<Integer> favouriteBank(@PathVariable("bankId") long bankId, HttpServletRequest request) {
        ThrowUtils.throwIf(bankId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankFavouriteService.toggleFavourite(bankId, loginUser));
    }

    /**
     * 取消收藏题单
     *
     * <p>{@code DELETE /question-bank-favourite/{bankId}}</p>
     */
    @DeleteMapping("/question-bank-favourite/{bankId}")
    public BaseResponse<Integer> unfavouriteBank(@PathVariable("bankId") long bankId,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(bankId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankFavouriteService.toggleFavourite(bankId, loginUser));
    }

    /**
     * 分页获取我的题单收藏
     *
     * <p>{@code POST /question-bank-favourite/my/list/page/vo}</p>
     */
    @PostMapping("/question-bank-favourite/my/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listMyFavouriteBank(
            @RequestBody QuestionBankFavouriteRequest queryRequest, HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankFavouriteService.pageMyFavouriteBank(queryRequest, loginUser));
    }

    // endregion
}
