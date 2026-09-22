package com.xly.codeforge.judge.service;

import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.model.dto.judge.RunJudgeRequest;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.SubmissionVO;

import java.util.List;

public interface JudgeService {

    /**
     * 执行判题（完整链路：读提交记录 → 抢占状态 → 跑沙箱 → 落库 → 回写终态）
     *
     * <p>这是<b>提交侧</b>的判题入口：会产生 submission、走 fencing 并发闸门、写回数据库。</p>
     *
     * @param questionSubmitId 提交记录 id
     * @return 判题后的提交记录 VO
     */
    SubmissionVO judgeSubmission(long questionSubmitId);

    /**
     * 试运行判题（不落库、不写状态、不抢租约）。
     *
     * <p><b>与 {@link #judgeSubmission} 的本质区别</b>：本方法只负责「把代码跑起来 + 判对错」，
     * 不读提交记录、不写数据库、不产生判题事件，也不碰 fencing / 心跳 / reaper ——
     * 试运行没有 submission 可绑，并发边界靠调用方（submission-service）的限流，而非租约。</p>
     *
     * <p><b>异步化（选项 3）</b>：本方法只做同步校验 + 生成 runId 立即返回，真正跑沙箱的部分
     * 放进独立线程池（{@code runWithJudgeExecutor}）异步执行，释放 Tomcat 线程。结果写入
     * {@code RunWithJudgeResultCache}，前端用返回的 runId 轮询 {@link #getRunWithJudgeResult}。</p>
     *
     * <p>用例由前端带来（可编辑，含期望输出），按序与沙箱逐用例对齐；题目配置
     * （时间/内存限制、SPJ）按 {@code questionId} 实时取，保证与正式判题同源。</p>
     *
     * @param req 试运行判题请求（题目 id / 代码 / 语言 / 可编辑用例）
     * @return 本次试运行的 runId（用于后续轮询结果）
     */
    String runAndJudge(RunJudgeRequest req);

    /**
     * 取试运行结果（轮询端点数据源）。
     *
     * <p>结果只存活在内存缓存（TTL 默认 60s），不落库。未就绪或已过期都返回 {@code null}，
     * 由调用方（前端）按客户端超时决定是否继续轮询 / 判超时重跑。</p>
     *
     * @param runId {@link #runAndJudge} 返回的试运行 id
     * @return 聚合判题结论 + 逐用例明细；未就绪/已过期返回 {@code null}
     */
    JudgeInfo getRunWithJudgeResult(String runId);
}
