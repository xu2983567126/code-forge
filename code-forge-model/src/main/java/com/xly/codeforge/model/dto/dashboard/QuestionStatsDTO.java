package com.xly.codeforge.model.dto.dashboard;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 题目域统计（question-service 属主）
 *
 * <p><b>为什么单独定义一份 DTO 而不是复用实体</b>：跨服务传输的应该是「已经算好的数字」，
 * 不是实体列表。聚合方（dashboard）不该有机会拿到题目内容 —— 它只需要计数。
 * 这也是 UltiCode 的 {@code DashboardAdminReadPort} 的做法：
 * 契约只返回 entity-free 的聚合数据，SQL 永远留在数据属主那边。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionStatsDTO implements Serializable {

    /**
     * 题目总数（未删除）
     */
    private Long totalCount;

    /**
     * 按难度分布的题目数：难度 → 数量
     *
     * <p>用 Map 而非固定字段：难度取值是 {@code 简单/中等/困难}，
     * 将来若增加难度（如「地狱」），这里不需要改结构。</p>
     */
    private Map<String, Long> difficultyDistribution;

    /**
     * 按标签分布的题目数：标签 → 数量
     *
     * <p>注意一个题目有多个标签，故各项之和 ≥ {@link #totalCount}，不是分区计数。</p>
     */
    private Map<String, Long> tagDistribution;

    /**
     * 题单总数（未删除）
     */
    private Long bankCount;

    @Serial
    private static final long serialVersionUID = 1L;
}
