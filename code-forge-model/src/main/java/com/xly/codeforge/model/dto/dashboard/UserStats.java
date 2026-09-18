package com.xly.codeforge.model.dto.dashboard;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 用户域统计
 *
 * @author xuxu
 */
@Data
public class UserStats implements Serializable {

    /**
     * 用户总数（未删除）
     */
    private Long totalCount;

    /**
     * 管理员数
     */
    private Long adminCount;

    /**
     * 被封禁用户数
     */
    private Long banCount;

    /**
     * 今日新增用户数
     */
    private Long todayNewCount;

    /**
     * 按角色分布：角色 → 数量
     */
    private Map<String, Long> roleDistribution;

    /**
     * 近 7 天每日新增用户数（已补零）
     */
    private List<SubmissionStatsDTO.DailyCount> recentDailyTrend;

    @Serial
    private static final long serialVersionUID = 1L;
}
