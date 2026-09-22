package com.xly.codeforge.client.service;

import com.xly.codeforge.model.dto.QuestionSubmissionStatsDTO;
import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Submission;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 提交记录服务
 *
 * <p>{@code name} / {@code path} 指向 submission-service 自己的
 * {@code /api/submission/inner}。</p>
 */
@HttpExchange("http://code-forge-submission/api/submission/inner")
public interface SubmissionFeignClient {

    @GetExchange("/{id}")
    Submission getSubmissionById(@PathVariable("id") long id);

    /**
     * 无条件覆盖写提交记录（判题完成后回写）。
     *
     * @deprecated 自 M1 起 judge-service 不再调用本接口，改用 fence 系列 CAS 接口
     * （{@code /fence/acquire}、{@code /fence/renew}、{@code /fence/verdict}、
     * {@code /fence/mark-failed}），否则并发判题会覆盖结果。本接口仅保留作管理端紧急修正通道。
     */
    @Deprecated
    @PostExchange("/update")
    boolean updateSubmissionById(@RequestBody Submission submission);

    /**
     * 判题并发 fencing：抢占租约（CAS）。返回 1=抢到；0=已被别人抢走或已非 WAITING。
     */
    @PostExchange("/fence/acquire")
    int acquireLease(@RequestBody SubmissionFenceRequest req);

    /**
     * 判题并发 fencing：心跳续租。返回 0 表示已丢租约（被 reaper 回收重派）。
     */
    @PostExchange("/fence/renew")
    int renewLease(@RequestBody SubmissionFenceRequest req);

    /**
     * 判题并发 fencing：写回判题结论（SUCCEED）。CAS 必须匹配 generation + attemptId。
     */
    @PostExchange("/fence/verdict")
    int writeVerdict(@RequestBody SubmissionVerdictRequest req);

    /**
     * 判题并发 fencing：判题失败兜底（FAILED + SYSTEM_ERROR）。CAS 同 {@code writeVerdict}。
     */
    @PostExchange("/fence/mark-failed")
    int markFailed(@RequestBody SubmissionFenceRequest req);

    /**
     * 加载提交域统计（dashboard 聚合用）
     *
     * @return 提交总数 / 今日数 / 通过率 / verdict 与语言分布 / 近 7 天趋势
     */
    @GetExchange("/stats")
    SubmissionStatsDTO getStats();

    /**
     * 加载某用户的提交热力图（每用户读，非 dashboard 全局聚合）
     *
     * @param userId 用户 id
     * @param days   统计窗口天数
     * @return 每日提交数（已补零）
     */
    @GetExchange("/user/heatmap")
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
    @PostExchange("/user/stats")
    List<SubmissionStatsItemDTO> listStatsByUserIds(@RequestBody List<Long> userIds);

    /**
     * 批量获取若干题目的提交统计（题库列表 / 题目详情展示通过率用）
     *
     * <p>提交统计属于提交域：{@code question.submit_num} / {@code accepted_num} 两列无人维护，
     * 故题目域不再自持计数，改由本接口实时取。</p>
     *
     * @param questionIds 题目 id 列表
     * @return 每道题的提交数 / 通过数；<b>只含有提交记录的题</b>，调用方需给缺行补 0
     */
    @PostExchange("/question/stats")
    List<QuestionSubmissionStatsDTO> listStatsByQuestionIds(@RequestBody List<Long> questionIds);
}
