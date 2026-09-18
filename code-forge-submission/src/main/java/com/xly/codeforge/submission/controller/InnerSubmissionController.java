package com.xly.codeforge.submission.controller;

import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.submission.service.SubmissionService;
import com.xly.codeforge.submission.service.SubmissionStatsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提交服务「内部接口」—— 供 judge-service 通过 Feign 调用。
 *
 * <p>完整路径 {@code /api/submission/inner/submission/get/id} 与
 * {@code /api/submission/inner/submission/update}，
 * 与 service-client 里 SubmissionFeignClient 的声明一一对应。</p>
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/inner")
public class InnerSubmissionController {

    @Resource
    private SubmissionService submissionService;

    @Resource
    private SubmissionStatsService submissionStatsService;

    /**
     * 根据 id 获取提交记录（Feign: GET /api/submission/inner/submission/get/id）
     */
    @GetMapping("/submission/get/id")
    public Submission getSubmissionById(@RequestParam("id") long id) {
        return submissionService.getById(id);
    }

    /**
     * 更新提交记录（判题完成后回写状态与判题信息）
     * （Feign: POST /api/submission/inner/submission/update）
     */
    @PostMapping("/submission/update")
    public boolean updateSubmissionById(@RequestBody Submission submission) {
        return submissionService.updateById(submission);
    }

    /**
     * 加载提交域统计（Feign: GET /api/submission/inner/stats）
     *
     * <p>供 dashboard 聚合用。</p>
     */
    @GetMapping("/stats")
    public SubmissionStatsDTO getStats() {
        return submissionStatsService.loadStats();
    }

    /**
     * 加载某用户的提交热力图（Feign: GET /api/submission/inner/user/heatmap）
     *
     * <p>这是「每用户」读，与全局看板统计（{@code GET /inner/stats}）不同——
     * 它服务于用户个人主页 / 管理端用户详情，不是 dashboard 聚合。
     * 命名上特意归到 {@code /user/} 下，与 UltiCode 的 SubmissionUserReadPort 对齐。</p>
     *
     * @param userId 用户 id
     * @param days   统计窗口天数（默认 365）
     */
    @GetMapping("/user/heatmap")
    public UserHeatmapDTO getHeatmap(@RequestParam("userId") long userId,
                                     @RequestParam(value = "days", defaultValue = "365") int days) {
        return submissionStatsService.loadHeatmap(userId, days);
    }

    /**
     * 批量获取若干用户的提交统计
     * （Feign: POST /api/submission/inner/user/stats）
     *
     * <p>这是「每用户」读，服务于管理端用户列表（每行列出提交数 / 通过题目数），
     * 不是 dashboard 全局聚合。归到 {@code /user/} 下与 UltiCode 的
     * AdminSubmissionUserDetailStatsReadPort 对齐。</p>
     *
     * <p>用 POST + body 而不是 GET + query：用户 id 列表整页可能有 20 个，
     * 拼进 URL 会撞长度限制，而且日志里一串 id 很难读。</p>
     *
     * @param userIds 用户 id 列表
     */
    @PostMapping("/user/stats")
    public List<SubmissionStatsItemDTO> listStatsByUserIds(@RequestBody List<Long> userIds) {
        return submissionStatsService.listStatsByUserIds(userIds);
    }
}
