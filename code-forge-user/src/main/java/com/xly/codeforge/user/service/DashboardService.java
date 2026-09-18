package com.xly.codeforge.user.service;

import com.xly.codeforge.model.dto.dashboard.DashboardStatsVO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.dto.dashboard.UserStats;
import com.xly.codeforge.model.entity.User;

/**
 * 数据看板服务
 *
 * <p><b>职责边界</b>：本服务只做「汇总」与「降级」，不写任何统计 SQL。
 * 用户域的数据从本地 {@code UserMapper} 取（本服务就是用户数据的属主），
 * 题目域和提交域通过 Feign 调各自服务的 {@code /inner/stats} 取。</p>
 *
 * <p>这样切分的原因见 {@code QuestionStatsService} 的类注释：统计 SQL 必须贴着数据写，
 * 聚合方拿到的是「已经算好的数字」。将来若把 dashboard 迁到独立服务，
 * 只需搬走这个 Service，三个域的 {@code /inner/stats} 一行都不用改。</p>
 *
 * @author xuxu
 */
public interface DashboardService {

    /**
     * 加载全站数据看板统计
     *
     * @return 三个域的统计汇总；某个域不可用时该块为 null 并记入降级标记
     */
    DashboardStatsVO loadStats();

    /**
     * 加载用户域统计（单独暴露，供用户中心页复用）
     *
     * @return 用户域统计
     */
    UserStats loadUserStats();

    /**
     * 加载指定用户的提交热力图
     *
     * @param userId 用户 id
     * @param days   统计窗口天数（1~365）
     * @return 热力图数据
     */
    UserHeatmapDTO loadHeatmap(Long userId, int days);

    /**
     * 加载某个用户的做题概览（做题数 / 通过数 / 通过率）
     *
     * @param user 目标用户
     * @return 该用户的统计；用户为 null 时返回空统计
     */
    UserStats loadUserStats(User user);
}
