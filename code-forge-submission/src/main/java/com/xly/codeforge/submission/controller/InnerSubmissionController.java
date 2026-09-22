package com.xly.codeforge.submission.controller;

import com.xly.codeforge.model.dto.QuestionSubmissionStatsDTO;
import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.submission.service.SubmissionService;
import com.xly.codeforge.submission.service.SubmissionStatsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提交服务「内部接口」—— 供 judge-service 通过 Feign 调用。
 *
 * <p>完整路径 {@code /api/submission/inner/{id}} 与
 * {@code /api/submission/inner/update}，
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
     * 根据 id 获取提交记录（Feign: GET /api/submission/inner/{id}）
     */
    @GetMapping("/{id}")
    public Submission getSubmissionById(@PathVariable("id") long id) {
        return submissionService.getById(id);
    }

    /**
     * 更新提交记录（判题完成后回写状态与判题信息）
     * （Feign: POST /api/submission/inner/update）
     *
     * @deprecated 自 M1 起 judge-service 不再调用本接口（改用 fence 系列 CAS 接口，
     * 见 {@code /fence/*}）。本接口仅保留作管理端紧急修正通道，无条件覆盖写、无 fencing，
     * 并发调用会覆盖判题结果，勿在判题链路中使用。
     */
    @Deprecated
    @PostMapping("/update")
    public boolean updateSubmissionById(@RequestBody Submission submission) {
        return submissionService.updateById(submission);
    }

    /**
     * 判题并发 fencing：抢占租约（Feign: POST /api/submission/inner/fence/acquire）
     */
    @PostMapping("/fence/acquire")
    public int acquireLease(@RequestBody SubmissionFenceRequest req) {
        return submissionService.acquireLease(req);
    }

    /**
     * 判题并发 fencing：心跳续租（Feign: POST /api/submission/inner/fence/renew）
     */
    @PostMapping("/fence/renew")
    public int renewLease(@RequestBody SubmissionFenceRequest req) {
        return submissionService.renewLease(req);
    }

    /**
     * 判题并发 fencing：写回判题结论（SUCCEED）（Feign: POST /api/submission/inner/fence/verdict）
     */
    @PostMapping("/fence/verdict")
    public int writeVerdict(@RequestBody SubmissionVerdictRequest req) {
        return submissionService.writeVerdict(req);
    }

    /**
     * 判题并发 fencing：判题失败兜底（Feign: POST /api/submission/inner/fence/mark-failed）
     */
    @PostMapping("/fence/mark-failed")
    public int markFailed(@RequestBody SubmissionFenceRequest req) {
        return submissionService.markFailed(req);
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

    /**
     * 批量获取若干题目的提交统计
     * （Feign: POST /api/submission/inner/question/stats）
     *
     * <p>服务于题库列表 / 题目详情的通过率展示。原先这两处读的是
     * {@code question.submit_num} / {@code accepted_num} 两个列，但它们运行期没有任何
     * 更新点（只有造数脚本插入时写死 0），前端通过率恒显示 0%。现改为读取时由本接口
     * 从提交表实时聚合，单一真相源留在数据属主（提交域）。</p>
     *
     * <p>归到 {@code /question/} 下，与 {@code /user/stats} 对称 —— 都是「按某维度批量统计」。</p>
     *
     * @param questionIds 题目 id 列表
     */
    @PostMapping("/question/stats")
    public List<QuestionSubmissionStatsDTO> listStatsByQuestionIds(@RequestBody List<Long> questionIds) {
        return submissionStatsService.listStatsByQuestionIds(questionIds);
    }
}
