package com.xly.codeforge.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.common.Result;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.utils.ResultUtils;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.dto.questionbankquestion.QuestionBankQuestionBulkRequest;
import com.xly.codeforge.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.xly.codeforge.model.entity.QuestionBankQuestion;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.question.service.QuestionBankQuestionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题单-题目关联接口
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/question-bank-question")
@Slf4j
public class QuestionBankQuestionController {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private UserFeignClient userFeignClient;

    /**
     * 向题单添加一道题目
     *
     * <p>{@code POST /question-bank-question}，body 里的 {@code questionBankId} /
     * {@code questionId} 直接复用查询请求类（只有两个 Long，没必要再定义一个类）。
     * 已在本题单中的题目不会报错，按幂等处理。</p>
     */
    @PostMapping
    public Result<Boolean> addQuestionToBank(@RequestBody QuestionBankQuestionQueryRequest addRequest,
                                             HttpServletRequest request) {
        if (addRequest == null || addRequest.getQuestionBankId() == null || addRequest.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userFeignClient.getLoginUser(request);
        questionBankQuestionService.addQuestionToBank(addRequest.getQuestionBankId(),
                addRequest.getQuestionId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 从题单移出一道题目
     *
     * <p>{@code DELETE /question-bank-question?questionBankId=&questionId=}。
     * 走 query 参数而不是 body —— 部分网关/客户端会丢弃 DELETE 的请求体。</p>
     *
     * <p>幂等：题目本就不在题单里时不报错，直接返回成功。</p>
     */
    @DeleteMapping
    public Result<Boolean> removeQuestionFromBank(@RequestParam("questionBankId") long questionBankId,
                                                  @RequestParam("questionId") long questionId,
                                                  HttpServletRequest request) {
        BusinessAssert.isTrue(questionBankId > 0 && questionId > 0, ErrorCode.INVALID_ID);
        User loginUser = userFeignClient.getLoginUser(request);
        questionBankQuestionService.removeQuestionFromBank(questionBankId, questionId, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量添加 / 移出题目
     *
     * <p>{@code POST /question-bank-question/bulk}，body 带 {@code action}（ADD / REMOVE）。
     * 为什么批量走 POST 而不是 {@code DELETE /xxx?ids=1,2,3}：后者受 URL 长度限制，
     * 几百道题就会超；且部分代理会丢掉 DELETE body（UltiCode 在注释里记录过这个坑）。</p>
     */
    @PostMapping("/bulk")
    public Result<Integer> bulkOperateQuestion(@RequestBody QuestionBankQuestionBulkRequest bulkRequest,
                                               HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankQuestionService.bulkOperateQuestion(bulkRequest, loginUser));
    }

    /**
     * 分页查询题单-题目关联（管理端用）
     *
     * <p>{@code POST /question-bank-question/list/page}</p>
     */
    @PostMapping("/list/page")
    public Result<Page<QuestionBankQuestion>> listQuestionBankQuestionByPage(
            @RequestBody QuestionBankQuestionQueryRequest queryRequest, HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = Math.max(queryRequest.getCurrent(), 1);
        long size = queryRequest.getPageSize();
        BusinessAssert.isTrue(size <= 20, ErrorCode.PARAMS_ERROR, "页大小不能超过 20");
        Page<QuestionBankQuestion> page = questionBankQuestionService.page(new Page<>(current, size),
                questionBankQuestionService.getQueryWrapper(queryRequest));
        return ResultUtils.success(page);
    }

    /**
     * 分页查询题单内的题目 id 列表
     *
     * <p>{@code GET /question-bank-question/{questionBankId}/question-ids}。返回 id 而非题目详情，
     * 题单详情页拿到 id 后再调题目分页接口取数据 —— 这样题单服务不需要跨服务拼题目信息。</p>
     */
    @GetMapping("/{questionBankId}/question-ids")
    public Result<Page<Long>> pageQuestionIdsInBank(@PathVariable("questionBankId") long questionBankId,
                                                    @RequestParam(value = "current", defaultValue = "1") long current,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        BusinessAssert.isTrue(pageSize <= 20, ErrorCode.PARAMS_ERROR, "页大小不能超过 20");
        return ResultUtils.success(questionBankQuestionService.pageQuestionIdsInBank(questionBankId,
                Math.max(current, 1), pageSize));
    }

    /**
     * 分页查询题单内的题目详情
     *
     * <p>{@code POST /question-bank-question/{questionBankId}/questions}。返回组装好的题目 VO，
     * 题单详情页一次请求即可渲染 —— 题单与题目同库同服务，服务端拼装不涉及跨服务调用，
     * 不必走「先取 id 再回查题目」的两段式。</p>
     *
     * <p>用 POST + body 而非 GET + query：与项目内既有分页接口保持一致，
     * 前端 {@code useInfiniteList} 也只发 body，走 GET 会多一套参数拼装分支。</p>
     */
    @PostMapping("/{questionBankId}/questions")
    public Result<Page<QuestionVO>> pageQuestionsInBank(@PathVariable("questionBankId") long questionBankId,
                                                        @RequestBody(required = false) QuestionBankQuestionQueryRequest queryRequest,
                                                        HttpServletRequest request) {
        QuestionBankQuestionQueryRequest req =
                queryRequest == null ? new QuestionBankQuestionQueryRequest() : queryRequest;
        long pageSize = req.getPageSize();
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 20, ErrorCode.PARAMS_ERROR, "页大小需在 1~20 之间");
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankQuestionService.pageQuestionsInBank(
                questionBankId, Math.max(req.getCurrent(), 1), pageSize, loginUser));
    }
}
