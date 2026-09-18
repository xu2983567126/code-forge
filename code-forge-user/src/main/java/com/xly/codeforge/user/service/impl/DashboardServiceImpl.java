package com.xly.codeforge.user.service.impl;

import com.xly.codeforge.common.constant.UserConstant;
import com.xly.codeforge.model.dto.dashboard.DashboardStatsVO;
import com.xly.codeforge.model.dto.dashboard.QuestionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.dto.dashboard.UserStats;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import com.xly.codeforge.user.mapper.UserMapper;
import com.xly.codeforge.user.service.DashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据看板服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    /**
     * 趋势图窗口：最近 7 天
     */
    private static final int TREND_DAYS = 7;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private UserMapper userMapper;

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Resource
    private SubmissionFeignClient submissionFeignClient;

    @Override
    public DashboardStatsVO loadStats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        // 记下取不到数据的域，前端据此显示「暂无数据」而不是 0
        List<String> degraded = new ArrayList<>();

        // 用户域：本地查询，但同样要兜底 —— 一条 SQL 写错就会让整个看板 50000，
        // 而契约要求单域不可用时只置空该块 + 记降级（见 DashboardService#loadStats）
        try {
            vo.setUserStats(loadUserStats());
        } catch (Exception e) {
            log.error("加载用户域统计失败，dashboard 该块降级", e);
            degraded.add("user");
        }

        // 题目域：跨服务，单独兜底 —— 一个域挂掉不该拖垮整页
        try {
            vo.setQuestionStats(questionFeignClient.getStats());
        } catch (Exception e) {
            log.error("加载题目域统计失败，dashboard 该块降级", e);
            degraded.add("question");
        }

        try {
            vo.setSubmissionStats(submissionFeignClient.getStats());
        } catch (Exception e) {
            log.error("加载提交域统计失败，dashboard 该块降级", e);
            degraded.add("submission");
        }

        vo.setDegradedDomains(degraded);
        return vo;
    }

    @Override
    public UserStats loadUserStats() {
        UserStats stats = new UserStats();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        stats.setTotalCount(orZero(userMapper.countUsers(null, null)));
        stats.setTodayNewCount(orZero(userMapper.countUsers(todayStart, null)));
        stats.setAdminCount(orZero(userMapper.countUsers(null, UserConstant.ADMIN_ROLE)));
        stats.setBanCount(orZero(userMapper.countUsers(null, UserConstant.BAN_ROLE)));

        // 角色分布：一条 GROUP BY 拿全
        Map<String, Long> roleMap = new LinkedHashMap<>();
        for (UserMapper.BucketCount row : userMapper.countByRole()) {
            roleMap.put(row.bucket() == null ? "unknown" : row.bucket(), orZero(row.cnt()));
        }
        stats.setRoleDistribution(roleMap);

        // 近 7 天新增趋势
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(TREND_DAYS - 1L);
        List<UserMapper.BucketCount> rows = userMapper.countByDay(
                startDate.atStartOfDay(), today.plusDays(1).atStartOfDay());
        Map<String, Long> dailyMap = rows.stream()
                .filter(row -> row.bucket() != null)
                .collect(Collectors.toMap(UserMapper.BucketCount::bucket,
                        row -> orZero(row.cnt()), Long::sum));
        stats.setRecentDailyTrend(fillDateGaps(dailyMap, startDate, today));
        return stats;
    }

    @Override
    public UserHeatmapDTO loadHeatmap(Long userId, int days) {
        // 热力图数据在提交域，转发即可 —— 本服务没有提交记录
        return submissionFeignClient.getHeatmap(userId, days);
    }

    @Override
    public UserStats loadUserStats(User user) {
        if (user == null) {
            return new UserStats();
        }
        // 目前「用户概览」与「全站用户统计」共用同一份实现。
        // 之所以保留这个重载而不是让调用方传 null：将来用户概览要加「该用户的
        // 做题数 / 通过率」时，签名不用变，在这里填即可。
        return loadUserStats();
    }

    /**
     * 把稀疏的「日期 → 数量」补成连续日期序列
     *
     * <p>理由同 {@code SubmissionStatsServiceImpl#fillDateGaps}：
     * {@code GROUP BY} 只返回有数据的日期，缺的格子会让前端折线图的时间轴失真。</p>
     */
    private List<SubmissionStatsDTO.DailyCount> fillDateGaps(Map<String, Long> dailyMap,
                                                             LocalDate startDate,
                                                             LocalDate endDate) {
        List<SubmissionStatsDTO.DailyCount> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMATTER);
            result.add(new SubmissionStatsDTO.DailyCount(key, dailyMap.getOrDefault(key, 0L)));
        }
        return result;
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
