package com.xly.codeforge.submission.service.impl;

import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.submission.mapper.SubmissionMapper;
import com.xly.codeforge.submission.service.SubmissionStatsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提交域统计服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class SubmissionStatsServiceImpl implements SubmissionStatsService {

    /**
     * 趋势图默认窗口：最近 7 天
     */
    private static final int TREND_DAYS = 7;

    /**
     * 热力图窗口上限（一年，与 GitHub 贡献图一致）
     */
    private static final int MAX_HEATMAP_DAYS = 365;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private SubmissionMapper submissionMapper;

    @Override
    public SubmissionStatsDTO loadStats() {
        SubmissionStatsDTO stats = new SubmissionStatsDTO();

        // 总数与今日数共用同一条 SQL，差异只在「是否传今日零点」
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        stats.setTotalCount(orZero(submissionMapper.countSubmissions(null)));
        stats.setTodayCount(orZero(submissionMapper.countSubmissions(todayStart)));

        // verdict 分布：同时算出通过数（避免再发一条 count SQL）
        Map<String, Long> verdictMap = toLinkedMap(submissionMapper.countByVerdict());
        stats.setVerdictDistribution(verdictMap);
        long accepted = verdictMap.getOrDefault(VerdictEnum.ACCEPTED.getCode(), 0L);
        stats.setAcceptedCount(accepted);

        // 通过率：分母为 0 时给 0 而不是 null，前端会直接乘 100 渲染
        long total = stats.getTotalCount();
        stats.setAcceptedRate(total == 0 ? 0.0
                : BigDecimal.valueOf(accepted)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .doubleValue());

        stats.setLanguageDistribution(toLinkedMap(submissionMapper.countByLanguage()));

        // 最近 7 天趋势：查 [今天-6天零点, 明天零点)
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        Map<String, Long> dailyMap = toLinkedMap(submissionMapper.countByDay(start, end, null));
        stats.setRecentDailyTrend(fillDateGaps(dailyMap, today.minusDays(TREND_DAYS - 1L), today));

        return stats;
    }

    @Override
    public UserHeatmapDTO loadHeatmap(Long userId, int days) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        // 收敛到 [1, 365]：days 来自前端 query 参数，不收敛的话
        // 传 days=100000 会让下面的日期循环造出十万个格子
        int window = Math.min(Math.max(days, 1), MAX_HEATMAP_DAYS);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(window - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        Map<String, Long> dailyMap = toLinkedMap(submissionMapper.countByDay(start, end, userId));
        List<SubmissionStatsDTO.DailyCount> counts = fillDateGaps(dailyMap, startDate, today);

        UserHeatmapDTO heatmap = new UserHeatmapDTO();
        heatmap.setDays(window);
        heatmap.setDailyCounts(counts);
        heatmap.setTotalCount(counts.stream()
                .map(SubmissionStatsDTO.DailyCount::getCount)
                .reduce(0L, Long::sum));
        // 有提交的天数：热力图上的「活跃天数」，前端用它算平均每天提交数
        heatmap.setActiveDays(counts.stream()
                .filter(count -> count.getCount() != null && count.getCount() > 0)
                .count());
        return heatmap;
    }

    @Override
    public List<SubmissionStatsItemDTO> listStatsByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            // 不抛异常而是返回空列表：这是「批量查询恰好没有目标」的正常情况，
            // 与「参数非法」不同。调用方（用户列表）会自行补 0。
            return new ArrayList<>();
        }
        List<Long> distinctIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return new ArrayList<>();
        }
        return submissionMapper.selectStatsByUserIds(distinctIds);
    }

    /**
     * 把「日期 → 数量」稀疏映射补成连续日期序列
     *
     * <p><b>为什么必须补零</b>：SQL 的 {@code GROUP BY} 只会返回有数据的日期。
     * 如果三天没做题，那三天直接不出现在结果里，前端画折线图时会把
     * 「周一、周四」画成相邻两点 —— 时间轴是失真的，而且看不出「中间断了」。
     * 热力图更明显：缺的格子会被后面的格子挤到错误的位置上。</p>
     *
     * @param dailyMap  稀疏映射（key 为 yyyy-MM-dd）
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 连续日期序列，每天一项
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

    /**
     * 把 mapper 的分组计数结果转成有序 Map
     *
     * <p>用 {@link LinkedHashMap} 保留 SQL 的排序（语言统计按数量降序返回）。</p>
     */
    private Map<String, Long> toLinkedMap(List<SubmissionMapper.BucketCount> rows) {
        if (rows == null || rows.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return rows.stream()
                .filter(row -> row.bucket() != null)
                .collect(Collectors.toMap(SubmissionMapper.BucketCount::bucket,
                        row -> orZero(row.cnt()),
                        Long::sum,
                        LinkedHashMap::new));
    }

    /**
     * null 归零：统计接口的返回值前端要直接参与算术，null 会让页面显示 NaN
     */
    private long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
