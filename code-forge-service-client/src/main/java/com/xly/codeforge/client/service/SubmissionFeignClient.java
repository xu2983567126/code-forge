package com.xly.codeforge.client.service;

import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.entity.Submission;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 提交记录服务
 *
 * <p>{@code name} / {@code path} 指向 submission-service 自己的
 * {@code /api/submission/inner}。</p>
 */
@FeignClient(name = "code-forge-submission", path = "/api/submission/inner")
public interface SubmissionFeignClient {

    @GetMapping("/submission/get/id")
    Submission getSubmissionById(@RequestParam("id") long id);

    @PostMapping("/submission/update")
    boolean updateSubmissionById(@RequestBody Submission submission);

    /**
     * 加载提交域统计（dashboard 聚合用）
     *
     * @return 提交总数 / 今日数 / 通过率 / verdict 与语言分布 / 近 7 天趋势
     */
    @GetMapping("/stats")
    SubmissionStatsDTO getStats();

    /**
     * 加载某用户的提交热力图（每用户读，非 dashboard 全局聚合）
     *
     * @param userId 用户 id
     * @param days   统计窗口天数
     * @return 每日提交数（已补零）
     */
    @GetMapping("/user/heatmap")
    UserHeatmapDTO getHeatmap(@RequestParam("userId") long userId,
                              @RequestParam("days") int days);

    /**
     * 批量获取若干用户的提交统计（管理端用户列表用，每用户读）
     *
     * <p>返回结果只包含有提交记录的用户，调用方需给缺行补 0。</p>
     *
     * @param userIds 用户 id 列表
     * @return 每个用户的提交数 / 通过题目数
     */
    @PostMapping("/user/stats")
    List<SubmissionStatsItemDTO> listStatsByUserIds(@RequestBody List<Long> userIds);
}
