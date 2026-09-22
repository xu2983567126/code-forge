package com.xly.codeforge.question.controller;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.common.Result;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.utils.ResultUtils;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.dto.questionbank.QuestionBankAddRequest;
import com.xly.codeforge.model.dto.questionbank.QuestionBankQueryRequest;
import com.xly.codeforge.model.dto.questionbank.QuestionBankUpdateRequest;
import com.xly.codeforge.model.entity.QuestionBank;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionBankVO;
import com.xly.codeforge.question.service.QuestionBankQuestionService;
import com.xly.codeforge.question.service.QuestionBankService;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题单接口
 *
 * <p><b>路径风格</b>：本模块按 RESTful 写（kebab-case 名词 + HTTP 方法表达动作）；
 * 项目内其余接口仍有 {@code /xxx/yyy} 动作式路径，将逐步收敛过来。</p>
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/question-bank")
@Slf4j
public class QuestionBankController {

    /**
     * 普通列表页大小上限（防爬虫）
     */
    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private UserFeignClient userFeignClient;

    /**
     * 创建题单
     *
     * <p>{@code POST /question-bank}。创建者从登录态取，请求体里没有 userId，
     * 前端无法伪造他人建题单。请求体可带 {@code questionIdList} 一次性把题加进去。</p>
     */
    @PostMapping
    public Result<Long> addQuestionBank(@RequestBody QuestionBankAddRequest addRequest,
                                        HttpServletRequest request) {
        BusinessAssert.notNull(addRequest, ErrorCode.EMPTY_REQUEST_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(addRequest, questionBank);
        questionBankService.validQuestionBank(questionBank, true);
        questionBank.setUserId(loginUser.getId());
        questionBank.setForkNum(0);
        if (questionBank.getIsPublic() == null) {
            // 不传默认公开：题单的核心价值是分享，私有应当是明确的主动选择
            questionBank.setIsPublic(1);
        }
        boolean saved = questionBankService.save(questionBank);
        BusinessAssert.isTrue(saved, ErrorCode.OPERATION_ERROR);

        // 带上初始题目：复用批量添加逻辑（内部已做存在性校验与去重）
        if (CollUtil.isNotEmpty(addRequest.getQuestionIdList())) {
            questionBankQuestionService.addQuestionsSilently(questionBank.getId(),
                    addRequest.getQuestionIdList(), loginUser.getId());
        }
        return ResultUtils.success(questionBank.getId());
    }

    /**
     * 删除题单
     *
     * <p>{@code DELETE /question-bank/{id}}。只有创建者或管理员能删。</p>
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteQuestionBank(@PathVariable("id") long id, HttpServletRequest request) {
        BusinessAssert.isTrue(id > 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        QuestionBank questionBank = questionBankService.getById(id);
        BusinessAssert.notNull(questionBank, ErrorCode.NOT_FOUND_ERROR, "题单不存在");
        // 越权按 404 处理，不泄露题单存在性
        questionBankService.checkBankEditAuth(questionBank, loginUser);
        boolean removed = questionBankService.removeById(id);
        return ResultUtils.success(removed);
    }

    /**
     * 修改题单
     *
     * <p>{@code PATCH /question-bank/{id}}。PATCH 而非 PUT：本接口只改元信息
     * （标题/描述/封面/公开性），题目组成由 {@code /question-bank-question} 单独管，
     * 语义上是「局部更新」而不是「整体替换」。</p>
     *
     * <p>权限：创建者本人或管理员，由 {@code checkBankEditAuth} 校验。</p>
     */
    @PatchMapping("/{id}")
    public Result<Boolean> updateQuestionBank(@PathVariable("id") long id,
                                              @RequestBody QuestionBankUpdateRequest updateRequest,
                                              HttpServletRequest request) {
        BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        if (updateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userFeignClient.getLoginUser(request);
        QuestionBank oldBank = questionBankService.getById(id);
        BusinessAssert.notNull(oldBank, ErrorCode.NOT_FOUND_ERROR);
        questionBankService.checkBankEditAuth(oldBank, loginUser);

        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(updateRequest, questionBank);
        questionBank.setId(id);
        questionBankService.validQuestionBank(questionBank, false);
        boolean result = questionBankService.updateById(questionBank);
        return ResultUtils.success(result);
    }

    /**
     * 获取题单（原始实体，仅创建者/管理员可见完整字段）
     *
     * <p>{@code GET /question-bank/{id}}</p>
     */
    @GetMapping("/{id}")
    public Result<QuestionBank> getQuestionBank(@PathVariable("id") long id, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankService.getQuestionBankById(id, loginUser));
    }

    /**
     * 获取题单视图（含统计与创建者信息）
     *
     * <p>{@code GET /question-bank/{id}/vo}</p>
     */
    @GetMapping("/{id}/vo")
    public Result<QuestionBankVO> getQuestionBankVO(@PathVariable("id") long id, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankService.getQuestionBankVOById(id, loginUser));
    }

    /**
     * 分页获取题单视图列表
     *
     * <p>{@code POST /question-bank/list/page/vo}。分页查询保留 POST + body，
     * 与项目内既有分页接口保持一致（查询条件字段多，放 URL 会超长）。</p>
     *
     * <p>可见性：默认只返回「公开的 OR 自己的」，避免私有题单泄露到列表里。</p>
     */
    @PostMapping("/list/page/vo")
    public Result<Page<QuestionBankVO>> listQuestionBankVOByPage(
            @RequestBody QuestionBankQueryRequest queryRequest, HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long size = queryRequest.getPageSize();
        BusinessAssert.isTrue(size <= MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "页大小不能超过 " + MAX_PAGE_SIZE);
        User loginUser = userFeignClient.getLoginUser(request);
        Page<QuestionBank> bankPage = questionBankService.page(
                new Page<>(Math.max(queryRequest.getCurrent(), 1), size),
                buildVisibleWrapper(queryRequest, loginUser));
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(bankPage, loginUser));
    }

    /**
     * 分页获取当前用户创建的题单
     *
     * <p>{@code POST /question-bank/my/list/page/vo}。userId 由后端强制覆盖为登录用户，
     * 前端传什么都无效 —— 这是防越权读他人题单列表的关键。</p>
     */
    @PostMapping("/my/list/page/vo")
    public Result<Page<QuestionBankVO>> listMyQuestionBankVOByPage(
            @RequestBody QuestionBankQueryRequest queryRequest, HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long size = queryRequest.getPageSize();
        BusinessAssert.isTrue(size <= MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "页大小不能超过 " + MAX_PAGE_SIZE);
        User loginUser = userFeignClient.getLoginUser(request);
        queryRequest.setUserId(loginUser.getId());
        Page<QuestionBank> bankPage = questionBankService.page(
                new Page<>(Math.max(queryRequest.getCurrent(), 1), size),
                questionBankService.getQueryWrapper(queryRequest));
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(bankPage, loginUser));
    }

    /**
     * fork（复制）题单
     *
     * <p>{@code POST /question-bank/{id}/fork}。把题目全量复制到自己的新题单，
     * 新题单默认私有（详见 Service 注释），源题单 fork 数 +1。</p>
     */
    @PostMapping("/{id}/fork")
    public Result<Long> forkQuestionBank(@PathVariable("id") long id, HttpServletRequest request) {
        BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionBankService.forkQuestionBank(id, loginUser));
    }

    /**
     * 组装「可见题单」的查询条件
     *
     * <p>规则：公开题单所有人可见；私有题单只有创建者（及管理员，管理员在
     * {@code checkReadAuth} 里单独放行）能看。用括号包住 OR 条件，
     * 否则会与关键词、userId 等条件串成错误的优先级。</p>
     */
    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QuestionBank> buildVisibleWrapper(
            QuestionBankQueryRequest queryRequest, User loginUser) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QuestionBank> wrapper =
                questionBankService.getQueryWrapper(queryRequest);
        if (userFeignClient.isAdmin(loginUser)) {
            // 管理员看全部，不加可见性过滤
            return wrapper;
        }
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        if (loginUserId == null) {
            // 匿名：只看公开题单
            wrapper.eq("is_public", 1);
        } else {
            // 若前端显式传了 isPublic，尊重它；否则「公开 OR 我的」
            Integer isPublic = queryRequest.getIsPublic();
            if (isPublic != null) {
                wrapper.eq("is_public", isPublic).eq("user_id", loginUserId);
            } else {
                wrapper.and(w -> w.eq("is_public", 1).or().eq("user_id", loginUserId));
            }
        }
        return wrapper;
    }
}
