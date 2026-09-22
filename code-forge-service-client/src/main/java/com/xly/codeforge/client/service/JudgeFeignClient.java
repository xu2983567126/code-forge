package com.xly.codeforge.client.service;


import com.xly.codeforge.model.dto.judge.RunJudgeRequest;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.vo.SubmissionVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("http://code-forge-judge/api/judge/inner")
public interface JudgeFeignClient {

    /**
     * 执行判题（提交侧：submission-service 经 MQ 派发后由消费者调）
     *
     * @param questionSubmitId 提交记录 id
     * @return 判题后的提交记录
     */
    @PostExchange("/do")
    SubmissionVO judgeSubmission(@RequestParam("questionSubmitId") long questionSubmitId);

    /**
     * 试运行判题（异步：立刻返回 runId，结果走轮询端点）
     *
     * <p>沙箱调用集中在 judge-service，submission-service 只做转发，
     * 避免出现第二套沙箱接入代码。</p>
     *
     * @param req 试运行判题请求（题目 id / 代码 / 语言 / 可编辑用例）
     * @return 本次试运行的 runId（用于后续轮询结果）
     */
    @PostExchange("/run-with-judge")
    String runWithJudge(@RequestBody RunJudgeRequest req);

    /**
     * 取试运行结果（轮询端点）
     *
     * <p>结果不落库，只在内存缓存短暂停留；未就绪/已过期返回 {@code null}。</p>
     *
     * @param runId 试运行 id
     * @return 聚合判题结论 + 逐用例明细；未就绪/已过期返回 {@code null}
     */
    @GetExchange("/run-with-judge/result/{runId}")
    JudgeInfo getRunWithJudgeResult(@PathVariable("runId") String runId);
}
