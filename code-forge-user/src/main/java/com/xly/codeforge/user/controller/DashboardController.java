package com.xly.codeforge.user.controller;

import com.xly.codeforge.common.annotation.AuthCheck;
import com.xly.codeforge.common.common.BaseResponse;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.common.ResultUtils;
import com.xly.codeforge.common.constant.UserConstant;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.model.dto.dashboard.DashboardStatsVO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.dto.dashboard.UserStats;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.user.service.DashboardService;
import com.xly.codeforge.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板接口（管理端）
 *
 * <p><b>管理端路径约定</b>：本类全部接口路径带 {@code /manage} 前缀
 * （{@code /dashboard/manage/...}），与 {@code UserController} 的约定一致 ——
 * 为将来抽独立管理服务预留迁移缝，届时网关把 {@code /api/*&#47;manage/**}
 * 整体路由过去，前端 URL 不变。</p>
 *
 * <p><b>本服务只做汇总与降级</b>：统计 SQL 全在数据属主服务
 * （question-service / submission-service 的 {@code /inner/stats}），
 * 本服务通过 Feign 取已经算好的数字。细节与理由见 {@code DashboardService} 类注释。</p>
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    /**
     * 热力图窗口上限（天）
     *
     * <p>不设上限时前端传 {@code days=100000} 会让下游造出十万个格子。</p>
     */
    private static final int MAX_HEATMAP_DAYS = 365;

    @Resource
    private DashboardService dashboardService;

    @Resource
    private UserService userService;

    /**
     * 全站数据看板统计（仅管理员）
     *
     * <p>{@code GET /dashboard/manage/stats}</p>
     *
     * <p>返回三个域的汇总。某个域拉取失败时该块为 null，且其名会出现在
     * {@code degradedDomains} 里 —— 前端据此显示「暂无数据」而不是 0。</p>
     */
    @GetMapping("/manage/stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<DashboardStatsVO> getDashboardStats(HttpServletRequest request) {
        return ResultUtils.success(dashboardService.loadStats());
    }

    /**
     * 用户域统计概览（仅管理员）
     *
     * <p>{@code GET /dashboard/manage/user-stats}。单独一个接口是为了让用户管理页
     * 不必拉整份看板数据（题目域/提交域在那页用不上）。</p>
     */
    @GetMapping("/manage/user-stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<UserStats> getUserStats(HttpServletRequest request) {
        return ResultUtils.success(dashboardService.loadUserStats());
    }

    /**
     * 指定用户的提交热力图
     *
     * <p>{@code GET /dashboard/heatmap?userId=&days=}。登录即可访问、不要求管理员 ——
     * 用户中心页展示自己的热力图；匿名请求退化为取登录态用户，
     * 因此不会出现「不传 userId 就能看别人数据」的口子。</p>
     *
     * @param userId 目标用户 id；不传则取当前登录用户
     * @param days   统计窗口天数，默认 365，上限 365
     */
    @GetMapping("/heatmap")
    public BaseResponse<UserHeatmapDTO> getHeatmap(@RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "days", defaultValue = "365") int days,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        long targetUserId = userId == null ? loginUser.getId() : userId;
        ThrowUtils.throwIf(targetUserId <= 0, ErrorCode.PARAMS_ERROR);
        // 窗口收敛放在这里也放在下游：上下游各自兜底，任一侧被改坏都不会造出异常数据量
        int safeDays = Math.min(Math.max(days, 1), MAX_HEATMAP_DAYS);
        return ResultUtils.success(dashboardService.loadHeatmap(targetUserId, safeDays));
    }
}
