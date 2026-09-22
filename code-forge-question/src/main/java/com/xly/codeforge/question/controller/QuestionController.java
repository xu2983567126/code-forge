package com.xly.codeforge.question.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.annotation.AuthCheck;
import com.xly.codeforge.common.common.*;
import com.xly.codeforge.common.constant.UserConstant;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.utils.ResultUtils;
import com.xly.codeforge.model.dto.question.*;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.judge.CodeTemplateGenerator;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionAdjacentVO;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.question.service.QuestionService;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题目接口
 *
 * <p><b>管理端路径约定</b>：仅管理员可用的接口，路径统一带 {@code /manage} 前缀
 * （如 {@code /question/manage/list/page}），为将来抽独立管理服务预留迁移缝 ——
 * 届时网关把 {@code /api/*&#47;manage/**} 整体路由过去，前端 URL 不变、零改动。</p>
 *
 */
@RestController
@RequestMapping("/")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserFeignClient userFeignClient;

    // region 增删改查

    /**
     * 创建
     *
     * @param questionCreateRequest
     * @param request
     * @return
     */
    @PostMapping("/create")
    public Result<Long> createQuestion(@RequestBody QuestionCreateRequest questionCreateRequest, HttpServletRequest request) {
        BusinessAssert.notNull(questionCreateRequest, ErrorCode.EMPTY_REQUEST_ERROR);
        Question question = toQuestion(questionCreateRequest);
        questionService.validQuestion(question, true);
        long newQuestionId = initQuestion(question, request);
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目（仅本人或管理员）
     *
     * <p><b>不级联清理题库/收藏/提交</b>：题目采用逻辑删除（{@code @TableLogic} + {@code is_delete}），
     * 只删除自身聚合，引用方（题库题目关联、收藏、提交记录）继续存在，
     * 但通过 {@code is_delete = 0} 自动过滤，不会出现外键断裂或孤儿引用。</p>
     *
     * @param id 题目 id（路径变量）
     * @param request 当前请求（用于身份与权限校验）
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteQuestion(@PathVariable("id") long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在（逻辑删除的题目不会被查到）
        Question oldQuestion = questionService.getById(id);
        BusinessAssert.notNull(oldQuestion, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可操作
        checkQuestionAuth(oldQuestion, request);
        boolean b = questionService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 修改题目（仅管理员）
     *
     * <p>{@code PATCH /question/{id}}。权限由 Service 内部按「本人 or 管理员」判定，
     * 前端只需调这一个端点。</p>
     *
     * <p><b>为什么不按权限拆成两个端点</b>：两个端点请求体字段完全相同、只有权限不同，
     * 前端传错就拿到 401，而错误信息看不出是权限问题。合成一个端点后，
     * 权限判断收敛在服务端一处。</p>
     *
     * @param id              题目 id（路径参数）
     * @param questionUpdateRequest 只带需要改的字段（PATCH 语义：null 字段不会被覆盖）
     */
    @PatchMapping("/{id}")
    public Result<Boolean> updateQuestion(@PathVariable("id") long id,
                                          @RequestBody QuestionUpdateRequest questionUpdateRequest,
                                          HttpServletRequest request) {
        if (questionUpdateRequest == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = toQuestion(questionUpdateRequest);
        question.setId(id);
        // 参数校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        BusinessAssert.notNull(oldQuestion, ErrorCode.NOT_FOUND_ERROR);
        checkQuestionAuth(oldQuestion, request);
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取题目视图（作者/管理员自动携带答案与测试用例，普通用户不携带）
     *
     * <p>路径变量风格：{@code GET /question/{id}/vo}，与题单模块的
     * {@code GET /question-bank/{id}} 保持一致，符合「按主键定位单资源」的 RESTful 语义。</p>
     *
     * @param id 题目 id（路径变量）
     * @return 题目视图对象
     */
    @GetMapping("/{id}/vo")
    public Result<QuestionVO> getQuestionVOById(@PathVariable("id") long id, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionService.getQuestionVOById(id, loginUser));
    }

    /**
     * 随机获取一道题（题库专题页「随机一题」）
     * <p>需要登录：{@code getLoginUser} 直读 Sa-Token 的 Redis token，匿名调用返回 40100。</p>
     *
     * @param notId 需要排除的题目 id（通常是当前正在浏览的题），可为空
     * @return 随机题目；题库为空或排除后无题时 data 为 null
     */
    @GetMapping("/random")
    public Result<QuestionVO> getRandomQuestion(Long notId, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionService.getRandomQuestionVO(notId, loginUser));
    }

    /**
     * 获取相邻题目（题目详情页「上一题 / 下一题」）
     *
     * @param id 当前题目 id
     * @return prev / next，首末题对应方向为 null
     */
    @GetMapping("/adjacent")
    public Result<QuestionAdjacentVO> getAdjacentQuestion(long id, HttpServletRequest request) {
        BusinessAssert.isTrue(id > 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionService.getAdjacentQuestion(id, loginUser));
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public Result<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                         HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        BusinessAssert.isTrue(size <= 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, loginUser));
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public Result<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                           HttpServletRequest request) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userFeignClient.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        BusinessAssert.isTrue(size <= 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, loginUser));
    }

    // endregion

    // 本项目用不到
    // /**
    //  * 分页搜索（从 ES 查询，封装类）
    //  *
    //  * @param questionQueryRequest
    //  * @param request
    //  * @return
    //  */
    // @PostMapping("/search/page/vo")
    // public Result<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
    //         HttpServletRequest request) {
    //     long size = questionQueryRequest.getPageSize();
    //     // 限制爬虫
    //     BusinessAssert.isTrue(size > 20, ErrorCode.PARAMS_ERROR);
    //     Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
    //     return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    // }

    /**
     * 分页获取题目列表（仅管理员，返回完整实体含答案与判题用例）
     *
     * <p>{@code POST /question/manage/list/page}</p>
     */
    @PostMapping("/manage/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                     HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 通过率取提交域的实时统计（submit_num/accepted_num 两列已废弃、恒 0）。
        // 填的是实体对象在本次响应里的值，不会写库。
        questionService.fillStatsForEntities(questionPage.getRecords());
        // 管理员权限已由 @AuthCheck 切面保证，此处无需再取登录用户
        return ResultUtils.success(questionPage);
    }

    private Question toQuestion(QuestionRequest request) {
        Question question = new Question();
        BeanUtils.copyProperties(request, question);
        List<String> tags = request.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = request.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        JudgeConfig judgeConfig = request.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 核心代码模式：codeTemplate 是 Solution 骨架时，driver 运行时由 CodeTemplateGenerator 派生，不落库。
        // 保存时仅做合法性预校验：骨架像 Solution 但类型不支持 / void 等直接拒存，避免提交后判题才报错。
        String codeTemplate = question.getCodeTemplate();
        if (codeTemplate != null && !codeTemplate.isBlank()) {
            try {
                CodeTemplateGenerator.deriveDriverCode(codeTemplate);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "题目 Solution 模板不合法：" + e.getMessage());
            }
        }
        return question;
    }

    private long initQuestion(Question question, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        question.setUserId(loginUser.getId());
        question.setFavourNum(0);
        question.setThumbNum(0);
        boolean result = questionService.save(question);
        BusinessAssert.isTrue(result, ErrorCode.OPERATION_ERROR);
        return question.getId();
    }

    /**
     * 校验当前用户对题目是否有编辑权（本人或管理员）
     *
     * 避免调用方先查一次、这里再查一次。PATCH 接口需要用实体做后续更新，故走这条。</p>
     *
     * @param oldQuestion 已查出的题目（不可为 null）
     * @param request     当前请求
     */
    private void checkQuestionAuth(Question oldQuestion, HttpServletRequest request) {
        User user = userFeignClient.getLoginUser(request);
        if (!oldQuestion.getUserId().equals(user.getId()) && !userFeignClient.isAdmin(user)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}

