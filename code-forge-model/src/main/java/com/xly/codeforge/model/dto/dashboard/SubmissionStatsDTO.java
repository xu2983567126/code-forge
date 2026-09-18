package com.xly.codeforge.model.dto.dashboard;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 提交域统计（submission-service 属主）
 *
 * @author xuxu
 */
@Data
public class SubmissionStatsDTO implements Serializable {

    /**
     * 提交总数（未删除）
     */
    private Long totalCount;

    /**
     * 今日提交数
     */
    private Long todayCount;

    /**
     * 通过数（verdict = ACCEPTED 的提交记录数）
     *
     * <p>注意这是「通过了多少次提交」，不是「多少道题被通过」——
     * 同一道题 AC 三次会算 3。要「通过题数」用 {@code DISTINCT question_id}。</p>
     */
    private Long acceptedCount;

    /**
     * 通过率（0~1），保留 4 位小数
     *
     * <p>分母为 0 时返回 0 而非 null —— 前端算百分比会直接乘 100，
     * null 会渲染成 {@code NaN%}。</p>
     */
    private Double acceptedRate;

    /**
     * 按判题结果分布：verdict code → 数量
     *
     * <p>含所有 verdict，包括 verdict 为 NULL 的历史记录（key 为 {@code UNKNOWN}）。
     * 不隐藏未知项 —— 藏起来会让前端饼图的各项之和对不上总数。</p>
     */
    private Map<String, Long> verdictDistribution;

    /**
     * 按编程语言分布：语言 → 数量
     */
    private Map<String, Long> languageDistribution;

    /**
     * 最近 7 天的每日提交数（按日期升序）
     *
     * <p><b>日期补零是必须的</b>：SQL 的 {@code GROUP BY} 只返回有提交的日期，
     * 中间断档的日子会直接消失，前端画折线图时时间轴会挤在一起。
     * 补零由 Service 侧完成（见 {@code SubmissionServiceImpl#fillDateGaps}）。</p>
     */
    private List<DailyCount> recentDailyTrend;

    /**
     * 每日计数项
     */
    @Data
    public static class DailyCount implements Serializable {

        /**
         * 日期，格式 yyyy-MM-dd
         */
        private String date;

        /**
         * 当天提交数
         */
        private Long count;

        @Serial
        private static final long serialVersionUID = 1L;

        public DailyCount() {
        }

        public DailyCount(String date, Long count) {
            this.date = date;
            this.count = count;
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
