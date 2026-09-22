package com.xly.codeforge.judge.controller;

import com.xly.codeforge.common.common.Result;
import com.xly.codeforge.common.utils.ResultUtils;
import com.xly.codeforge.judge.service.JudgeService;
import com.xly.codeforge.model.dto.judge.RunJudgeRequest;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.vo.SubmissionVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 判题服务「内部接口」—— 供 submission-service 通过 Feign 调用。
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/inner")
public class InnerJudgeController {

    @Resource
    private JudgeService judgeService;

    /**
     * 执行判题（提交侧，Feign: POST /api/judge/inner/do）
     *
     * <p>会产生 submission、走 fencing 并发闸门、写回数据库。与 {@code /run-with-judge}
     * （试运行判题，不落库）区分开，避免调用方混淆两条链路。</p>
     *
     * @param questionSubmitId 提交记录 id
     * @return 判题后的提交记录 VO
     */
    @PostMapping("/do")
    public SubmissionVO judgeSubmission(@RequestParam("questionSubmitId") long questionSubmitId) {
        return judgeService.judgeSubmission(questionSubmitId);
    }

    /**
     * 试运行判题（Feign: POST /api/judge/inner/run-with-judge）
     *
     * <p>沙箱调用逻辑全部留在本服务 —— submission-service 没有 {@code sandbox.type} 配置、
     * 没有 {@code SandboxFactory}，让它直连沙箱会形成第二套沙箱接入代码。</p>
     *
     * <p>异步化（选项 3）：本端点只做校验 + 返回 runId，真正跑沙箱在独立线程池异步执行，
     * 结果由 {@link #getRunWithJudgeResult} 经 runId 轮询给出。</p>
     *
     * <p>入参用 {@code @RequestBody} 包成 {@code RunJudgeRequest}：用例是列表（输入 + 期望输出），
     * 用 body 比逐字段 {@code @RequestParam} 更稳，也不受 URL 长度限制。</p>
     *
     * @param req 试运行判题请求（题目 id / 代码 / 语言 / 可编辑用例）
     * @return 本次试运行的 runId（用于后续轮询结果）
     */
    @PostMapping("/run-with-judge")
    public String runWithJudge(@RequestBody RunJudgeRequest req) {
        return judgeService.runAndJudge(req);
    }

    /**
     * 取试运行结果（Feign: GET /api/judge/inner/run-with-judge/result/{runId}）
     *
     * <p>run-with-judge 不落库，结果只存活在内存缓存（TTL 默认 60s）。未就绪或已过期都返回
     * {@code data: null}，由前端按客户端超时决定是否继续轮询 / 判超时重跑。</p>
     *
     * @param runId {@link #runWithJudge} 返回的试运行 id
     * @return 聚合判题结论 + 逐用例明细；未就绪/已过期返回 {@code null}
     */
    @GetMapping("/run-with-judge/result/{runId}")
    public Result<JudgeInfo> getRunWithJudgeResult(@PathVariable("runId") String runId) {
        return ResultUtils.success(judgeService.getRunWithJudgeResult(runId));
    }
}
