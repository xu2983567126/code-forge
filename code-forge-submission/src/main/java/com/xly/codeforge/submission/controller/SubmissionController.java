package com.xly.codeforge.submission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.common.BaseResponse;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.common.ResultUtils;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.model.dto.judge.RunCodeRequest;
import com.xly.codeforge.model.dto.submission.SubmissionAddRequest;
import com.xly.codeforge.model.dto.submission.SubmissionQueryRequest;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.RunCodeVO;
import com.xly.codeforge.model.vo.SubmissionVO;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.submission.service.SubmissionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 题目提交接口
 *
 * <p><b>context-path 已是 {@code /api/submission}</b>，故这里的映射一律从根开始写，
 * 完整路径为 {@code /api/submission/...}。类上映射保持 {@code "/"}：再套一层
 * {@code /submissions} 会与网关的 {@code /api/submission/**} 重复一层。</p>
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/")
@Slf4j
public class SubmissionController {

    @Resource
    private SubmissionService submissionService;

    @Resource
    private UserFeignClient userFeignClient;

    /**
     * 提交题目（正式提交，落库 + 异步判题）
     *
     * <p>{@code POST /submission/submit}</p>
     *
     * @return 提交记录的 ID
     */
    @PostMapping("/submit")
    public BaseResponse<Long> submit(@RequestBody SubmissionAddRequest submissionAddRequest,
                                     HttpServletRequest request) {
        if (submissionAddRequest == null || submissionAddRequest.getQuestionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能提交
        final User loginUser = userFeignClient.getLoginUser(request);
        long submissionId = submissionService.submit(submissionAddRequest, loginUser);
        return ResultUtils.success(submissionId);
    }

    /**
     * 试运行代码（不落库、同步返回）
     *
     * <p>{@code POST /submission/run}。用于题目详情页的「运行」按钮 ——
     * 用户拿样例输入试一下代码，不该在提交记录里留下一行。</p>
     */
    @PostMapping("/run")
    public BaseResponse<RunCodeVO> runCode(@RequestBody RunCodeRequest runCodeRequest, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(submissionService.runCode(runCodeRequest, loginUser));
    }

    /**
     * 分页获取提交记录列表
     *
     * <p>{@code POST /submission/list/page/vo}。数据范围见
     * {@link SubmissionService#applyDataScope}：普通用户只能看自己的，
     * 管理员可看全站（不传 {@code userId}）或指定用户。</p>
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SubmissionVO>> listSubmissionByPage(@RequestBody SubmissionQueryRequest submissionQueryRequest,
                                                                 HttpServletRequest request) {
        if (submissionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = Math.max(submissionQueryRequest.getCurrent(), 1);
        long size = submissionQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "页大小不能超过 20");
        User loginUser = userFeignClient.getLoginUser(request);
        // 必须在构造 QueryWrapper 之前收敛范围，否则请求里的 userId 会直接进 SQL
        submissionService.applyDataScope(submissionQueryRequest, loginUser);
        // 得到原始提交记录
        Page<Submission> submissionPage = submissionService.page(new Page<>(current, size),
                submissionService.getQueryWrapper(submissionQueryRequest));
        return ResultUtils.success(submissionService.getSubmissionVOPage(submissionPage, loginUser));
    }

    /**
     * 按 id 获取单条提交记录详情
     *
     * <p>{@code GET /submission/{id}/vo}，命名与 question 服务的
     * {@code /question/{id}/vo} 对齐。详情页是独立路由，刷新后必须能就地按 id 取数。</p>
     *
     * <p>读取权限：<b>仅本人与管理员</b>，越权报 40101 而不是返回脱敏结果。
     * 本服务<b>没有</b> Sa-Token 拦截器（无全局兜底网），故每个读取端点都必须
     * 自己走这一步校验，漏写即等于全公开。</p>
     */
    @GetMapping("/{id}/vo")
    public BaseResponse<SubmissionVO> getSubmissionVOById(@PathVariable("id") long id, HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(submissionService.getSubmissionVOById(id, loginUser));
    }

    /**
     * 获取当前用户在某题上的最佳提交
     *
     * <p>{@code GET /submission/best?questionId=}。题目详情页用它展示
     * 「我的最近/最优提交」，避免前端再拉一次分页列表再自己挑。</p>
     *
     * <p>无提交时返回 {@code data: null}，不报错 —— 这是最常见的情况（第一次做题）。</p>
     */
    @GetMapping("/best")
    public BaseResponse<SubmissionVO> getBestSubmission(@RequestParam("questionId") long questionId,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(submissionService.getBestSubmission(questionId, loginUser));
    }

    /**
     * 批量查询当前用户对多道题是否已通过
     *
     * <p>{@code POST /submission/solved}，body 是题目 id 列表。
     * 题目列表页要对每一行标注「已通过」，逐题调 {@code /best} 就是 N+1。</p>
     *
     * @return 题目 id → 是否已 AC
     */
    @PostMapping("/solved")
    public BaseResponse<Map<Long, Boolean>> mapSolvedQuestions(@RequestBody List<Long> questionIds,
                                                               HttpServletRequest request) {
        if (questionIds == null || questionIds.isEmpty()) {
            return ResultUtils.success(Map.of());
        }
        ThrowUtils.throwIf(questionIds.size() > 200, ErrorCode.PARAMS_ERROR, "单次最多查询 200 道题");
        User loginUser = userFeignClient.getLoginUser(request);
        return ResultUtils.success(submissionService.mapSolvedQuestions(questionIds, loginUser.getId()));
    }

    /**
     * 获取全部判题结果选项（供前端渲染筛选下拉与标签颜色）
     *
     * <p>{@code GET /submission/verdicts}</p>
     */
    @GetMapping("/verdicts")
    public BaseResponse<List<Map<String, String>>> listVerdictOptions() {
        return ResultUtils.success(submissionService.listVerdictOptions());
    }

    /**
     * 回填历史数据的 verdict 列（管理端运维接口）
     *
     * <p>{@code POST /submission/manage/backfill-verdict}。verdict 是后加的列，
     * 旧数据该列全为 NULL。幂等：重复执行只处理仍为 NULL 的记录。</p>
     *
     * <p><b>为什么用显式校验而不是 {@code @AuthCheck}</b>：本服务<b>没有</b>
     * {@code AuthInterceptor} 切面（只有 question / user 两个服务有）。
     * 在缺失切面的服务上标 {@code @AuthCheck} 注解会被 Spring 静默忽略 ——
     * 看起来加了权限校验，实际是全开的，比不写注解更危险。
     * 故这里直接调 Feign 查角色，行为可见、可验证。</p>
     */
    @PostMapping("/manage/backfill-verdict")
    public BaseResponse<Integer> backfillVerdict(HttpServletRequest request) {
        User loginUser = userFeignClient.getLoginUser(request);
        ThrowUtils.throwIf(!userFeignClient.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        return ResultUtils.success(submissionService.backfillVerdict());
    }
}
