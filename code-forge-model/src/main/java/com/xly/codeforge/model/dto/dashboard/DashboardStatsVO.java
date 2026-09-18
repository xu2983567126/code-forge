package com.xly.codeforge.model.dto.dashboard;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 全站 dashboard 统计
 *
 * <p>由 user-service 聚合：用户域（本地查询）+ 题目域 / 提交域（Feign 调
 * question / submission 的 {@code /inner/stats}）。</p>
 *
 * <p><b>结构上的设计取舍</b>：这里把三个域的统计平铺在一个 VO 里，
 * 而不是让前端分别调三个接口。理由是 dashboard 页面一次性渲染全部卡片，
 * 分三次请求会让用户看到「数字一个个跳出来」的割裂感；
 * 而且三个统计块彼此独立，任何一个域挂掉时可以用空值降级，不影响其它块。</p>
 *
 * @author xuxu
 */
@Data
public class DashboardStatsVO implements Serializable {

    /**
     * 用户域统计
     */
    private UserStats userStats;

    /**
     * 题目域统计（来自 question-service）
     */
    private QuestionStatsDTO questionStats;

    /**
     * 提交域统计（来自 submission-service）
     */
    private SubmissionStatsDTO submissionStats;

    /**
     * 降级标记：哪些域的数据没取到
     *
     * <p>为空表示全部正常。非空时前端应把对应卡片显示成「暂无数据」而不是 0 ——
     * 「服务挂了」和「真的是 0」对用户是完全不同的信息，用 0 冒充会误导。</p>
     */
    private List<String> degradedDomains;

    @Serial
    private static final long serialVersionUID = 1L;
}
