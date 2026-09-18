package com.xly.codeforge.model.dto.dashboard;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户提交热力图（提交域，按用户维度）
 *
 * <p>结构与 GitHub 贡献图一致：一串「日期 + 当天提交数」，前端直接铺格子。</p>
 *
 * @author xuxu
 */
@Data
public class UserHeatmapDTO implements Serializable {

    /**
     * 统计窗口的天数（默认 365，即过去一年）
     */
    private Integer days;

    /**
     * 窗口内总提交数
     */
    private Long totalCount;

    /**
     * 窗口内有提交的天数（用于算「连续打卡」与活跃度）
     */
    private Long activeDays;

    /**
     * 每天的提交数，按日期升序，<b>已补零</b>
     *
     * <p>补零的原因同 {@link SubmissionStatsDTO#getRecentDailyTrend()}：
     * 热力图需要连续的时间轴，缺的格子会被前端画错位置。</p>
     */
    private List<SubmissionStatsDTO.DailyCount> dailyCounts;

    @Serial
    private static final long serialVersionUID = 1L;
}
