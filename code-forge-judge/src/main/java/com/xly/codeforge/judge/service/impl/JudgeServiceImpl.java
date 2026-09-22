package com.xly.codeforge.judge.service.impl;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.judge.sandbox.*;
import com.xly.codeforge.judge.service.JudgeService;
import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.judge.strategy.JudgeStrategyManager;
import com.xly.codeforge.judge.config.JudgeFenceConfig;
import com.xly.codeforge.judge.config.SandboxConcurrencyLimiter;
import com.xly.codeforge.judge.metrics.JudgeMetrics;
import com.xly.codeforge.judge.service.RunWithJudgeResultCache;
import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.judge.testcase.TestCaseProvider;
import com.xly.codeforge.model.dto.judge.RunCase;
import com.xly.codeforge.model.dto.judge.RunJudgeRequest;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.enums.SubmissionLanguageEnum;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.SandboxCodeAssembler;
import com.xly.codeforge.model.judge.CodeTemplateGenerator;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.QuestionAdminVO;
import com.xly.codeforge.model.vo.SubmissionVO;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.xly.codeforge.model.enums.SubmissionStatusEnum.*;


@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    /**
     * 试运行时无题目配置可依据，用一份宽松默认值兜底（单位 ms）
     */
    private static final long DEFAULT_TIME_LIMIT = 1000L;

    /**
     * 试运行默认内存限制（单位 KB，即 100MB）
     */
    private static final long DEFAULT_MEMORY_LIMIT = 102400L;

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Resource
    private SubmissionFeignClient submissionFeignClient;

    @Value("${sandbox.type}")
    private String codesandboxType;

    @Resource
    JudgeStrategyManager judgeStrategyManager;

    /**
     * 测试用例提供者（当前 = 数据库实现；未来可切对象存储而不动判题逻辑）。
     * 判题主体只认 {@code TestCaseProvider} 契约，不感知用例来源与存储细节。
     */
    @Resource
    TestCaseProvider testCaseProvider;

    /**
     * 判题心跳续租线程池（M1）：抢到租约后定时 {@code renewLease}，保证长任务不被 reaper 误杀。
     */
    @Resource
    private ScheduledExecutorService judgeHeartbeatExecutor;

    /**
     * 判题链路可观测指标（P0 / M7）。fencing 拦截的重复/过期判题在此累加。
     * 用 {@code @Resource} 注入，手动 new 的单元测试里该字段为 null，调用处已做 null 兜底，不会 NPE。
     */
    @Resource
    private JudgeMetrics judgeMetrics;

    /**
     * 沙箱并发配额（唯一真相源，默认 4）：正式判题 / 试运行 / SPJ 经 {@code SandboxProxy}
     * 共享这一把信号量，保证总在途沙箱调用 ≤ 4，不触发沙箱「并发已达上限」拒绝。
     */
    @Resource
    private SandboxConcurrencyLimiter sandboxConcurrencyLimiter;

    /**
     * 试运行异步线程池（选项 3）：控制器立即返回 runId，池线程抢沙箱配额、跑沙箱、判题、
     * 把结果写进 {@code RunWithJudgeResultCache}。池大小与沙箱配额对齐（4），超出部分在
     * {@code SandboxProxy.executeCode} 处排队，不会增加沙箱吞吐。
     */
    @Resource
    private ThreadPoolTaskExecutor runWithJudgeExecutor;

    /**
     * 试运行结果缓存：run-with-judge 不落库，结果只在内存里短暂停留供前端轮询。
     */
    @Resource
    private RunWithJudgeResultCache runWithJudgeResultCache;

    @Override
    public SubmissionVO judgeSubmission(long questionSubmitId) {
        // 1. 读提交记录（含代次号 generation，供 fencing CAS 使用）
        Submission submission = submissionFeignClient.getSubmissionById(questionSubmitId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交记录不存在！");
        }
        // 2. 抢占判题权（fencing）：CAS acquireLease。只有「持租约且代次匹配」者能进入判题主体，
        //    并发的第二次 judge 会在这里被挡掉（返回 0），不会 double-judge、不会覆盖结果。
        //    抢不到就直接返回，连题目服务都不打 —— 重复分发不打额外 RPC，也杜绝 Pandora 式 re-dispatch。
        long generation = submission.getGeneration() == null ? 1L : submission.getGeneration();
        String attemptId = UUID.randomUUID().toString();
        SubmissionFenceRequest fence = new SubmissionFenceRequest();
        fence.setId(questionSubmitId);
        fence.setAttemptId(attemptId);
        fence.setGeneration(generation);
        fence.setTtlSeconds(JudgeFenceConfig.LEASE_TTL_SECONDS);
        int acquired = submissionFeignClient.acquireLease(fence);
        if (acquired == 0) {
            // 别人已抢 / 已终态 / 重复事件（已 RUNNING 的重复分发）—— 静默丢弃，
            // 不抛"题目正在判题中"，杜绝 Pandora 式 re-dispatch 死循环。
            log.info("judgeSubmission 被 fencing 挡掉（并发重复判题），submissionId={}, generation={}",
                    questionSubmitId, generation);
            if (judgeMetrics != null) {
                judgeMetrics.incrementFenceDropped();
            }
            return null;
        }

        // 3. 抢到租约后再拉题目信息（fencing 之后再取，避免被挡掉的重复分发白白打 question 服务）。
        //    题目缺失属数据异常，直接上抛交由兜底处理。
        Long questionId = submission.getQuestionId();
        Question question = questionFeignClient.getQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目信息不存在！");
        }

        // 4. 启心跳续租，保证长任务不被 reaper 误杀；finally 里取消，避免任务泄漏。
        ScheduledFuture<?> heartbeat = judgeHeartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        submissionFeignClient.renewLease(fence);
                    } catch (Exception e) {
                        log.warn("判题心跳续租失败，submissionId={}", questionSubmitId, e);
                    }
                },
                JudgeFenceConfig.HEARTBEAT_SECONDS, JudgeFenceConfig.HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        try {
            // 5. 判题主体。从这一刻起提交已持租约，任何异常都由下方兜底推到终态，
            //    否则该提交会永久停留在「判题中」（历史故障：沙箱不可用 → 提交全部变僵尸记录）。
            // questionAdminVO 仅用于取 judgeConfig / spjCode / spjLanguage（非用例，不涉及存储演进）
            QuestionAdminVO questionAdminVO = QuestionAdminVO.objToVo(question);
            // 用例经提供者解析（当前 = 数据库实现；未来可切对象存储而不动此处）。
            // 直接复用上方已抢到租约后才拉取的 question，避免同题双拉。
            List<TestCaseData> testCases = testCaseProvider.getTestCases(question);
            List<String> inputList = testCases.stream()
                    .map(TestCaseData::getInput)
                    .toList();

            String language = submission.getLanguage();
            Sandbox sandbox = SandboxFactory.newInstance(codesandboxType);
            SandboxProxy codeSandboxProxy = new SandboxProxy(sandbox, sandboxConcurrencyLimiter);
            String userCode = submission.getCode();
            // 核心代码模式：driver 不落库，运行时由 codeTemplate 派生；普通题 codeTemplate 为空则派生为 null 回落
            String driverCode = CodeTemplateGenerator.deriveDriverCode(question.getCodeTemplate());
            // 核心代码模式：用户只实现方法，driver（含 Main）由题目提供并拼在用户代码之后
            SandboxCodeAssembler.checkUserCode(userCode, driverCode);
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .inputList(inputList)
                    .code(SandboxCodeAssembler.assemble(userCode, driverCode))
                    .language(language)
                    // 驱动程序按 argv 给出的路径读输入文件，不依赖标准输入
                    .inputMode(ExecuteCodeRequest.INPUT_MODE_FILE)
                    .build();
            ExecuteCodeResponse executeCodeResponse = codeSandboxProxy.executeCode(executeCodeRequest);

            JudgeContext judgeContext = JudgeContext.builder()
                    .judgeCases(testCases)
                    // 正式判题：用例来自题库，必须每个都带期望输出。缺失即题目配置有问题，
                    // 由 VerdictResolver 显式判 SYSTEM_ERROR 并写 detail，不再静默落入中性态。
                    .requireExpectedOutput(true)
                    .judgeConfig(questionAdminVO.getJudgeConfig())
                    .language(language)
                    // 特判程序源码与语言：题目配置 compareMode=SPJ 时由 VerdictResolver 交给 SpecialJudgeExecutor 执行
                    .spjCode(questionAdminVO.getSpjCode())
                    .spjLanguage(questionAdminVO.getSpjLanguage())
                    .sandboxCaseResults(executeCodeResponse.getCaseResults())
                    .compileError(executeCodeResponse.getCompileError())
                    .systemError(executeCodeResponse.getSystemError())
                    .sandboxMessage(executeCodeResponse.getMessage())
                    .build();
            JudgeInfo judgeInfo = judgeStrategyManager.doJudge(judgeContext);

            // 6. 写回判题结论（fenced）：CAS 必须同时匹配 generation + attemptId。
            SubmissionVerdictRequest verdict = new SubmissionVerdictRequest();
            verdict.setId(questionSubmitId);
            verdict.setGeneration(generation);
            verdict.setAttemptId(attemptId);
            verdict.setStatus(SUCCEED.getValue());
            // 把判题结论从 judgeInfo 里抽出，单独落 verdict 列（供筛选 / 统计走索引）。
            // JudgeInfo.message 现已是 VerdictEnum，取其 code 落库，与 verdict 列同码。
            verdict.setVerdict(judgeInfo.getMessage().getCode());
            verdict.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
            int rows = submissionFeignClient.writeVerdict(verdict);
            if (rows == 0) {
                // 结果已 stale（被 reaper 回收重派），丢弃不写。
                log.warn("judge 结论已 stale 被丢弃，submissionId={}", questionSubmitId);
                if (judgeMetrics != null) {
                    judgeMetrics.incrementFenceDropped();
                }
                return null;
            }
        } catch (Exception e) {
            // 【兜底】判题链路异常（沙箱不可用 / 调用超时 / 策略异常）→ 回写 FAILED（fenced），
            // 保证提交不会永远卡在「判题中」。
            log.error("判题异常，submissionId={}，已回写为 FAILED", questionSubmitId, e);
            SubmissionFenceRequest failFence = new SubmissionFenceRequest();
            failFence.setId(questionSubmitId);
            failFence.setAttemptId(attemptId);
            failFence.setGeneration(generation);
            int rows = submissionFeignClient.markFailed(failFence);
            if (rows == 0) {
                // 已丢租约（被 reaper 回收重派），跳过 —— reaper 会重判，不要写失效结果。
                log.warn("markFailed 被 fencing 挡掉（已丢租约），submissionId={}", questionSubmitId);
                if (judgeMetrics != null) {
                    judgeMetrics.incrementFenceDropped();
                }
            }
            // 上抛保留错误可见性：调用方 JudgeMqConsumer 已捕获并仅记日志，不会引发重试。
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题失败：" + e.getMessage());
        } finally {
            heartbeat.cancel(false);
        }

        Submission submit = submissionFeignClient.getSubmissionById(questionSubmitId);
        return SubmissionVO.objToVo(submit);
    }

    @Override
    public String runAndJudge(RunJudgeRequest req) {
        // 1. 同步校验：参数错误必须立即返回 400，不能丢进异步池（池里抛异常拿不到 400）
        BusinessAssert.notNull(req, ErrorCode.EMPTY_REQUEST_ERROR);
        String code = req.getCode();
        String language = req.getLanguage();
        BusinessAssert.notBlank(new String[]{code, language}, ErrorCode.PARAMS_ERROR, "代码与语言不能为空");
        // 语言必须合法：脏值会在沙箱侧以系统错误暴露成 500，这里挡掉给更准确的 400 提示
        BusinessAssert.notNull(SubmissionLanguageEnum.getEnumByValue(language), ErrorCode.PARAMS_ERROR, "编程语言错误");
        List<RunCase> cases = req.getCases();
        BusinessAssert.notEmpty(cases, ErrorCode.PARAMS_ERROR, "用例不能为空");

        Long questionId = req.getQuestionId();
        BusinessAssert.positive(questionId, ErrorCode.INVALID_ID);
        Question question = questionFeignClient.getQuestionById(questionId);
        BusinessAssert.notNull(question, ErrorCode.NOT_FOUND_ERROR, "题目不存在");

        // 2. 生成 runId 并立即返回：真正跑沙箱的部分放进独立线程池异步执行，
        //    释放 Tomcat 线程（否则多个用户同时试运行会占满 Tomcat 线程，连登录/浏览都卡）。
        //    结果写入 RunWithJudgeResultCache，前端用 runId 轮询。
        String runId = UUID.randomUUID().toString();
        try {
            runWithJudgeExecutor.execute(() -> {
                try {
                    JudgeInfo info = doRunWithJudge(req, question);
                    runWithJudgeResultCache.put(runId, info);
                } catch (Exception e) {
                    log.error("试运行判题失败，runId={}", runId, e);
                    // 跑挂了也写一条结果，前端轮询拿到错误而非一直「未就绪」超时
                    runWithJudgeResultCache.put(runId, buildSystemErrorInfo(e));
                }
            });
        } catch (RejectedExecutionException e) {
            // 池满（试运行并发超限）：直接 429，前端引导稍后重试，不堆积、不阻塞 Tomcat
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "试运行繁忙，请稍后再试");
        }
        return runId;
    }

    @Override
    public JudgeInfo getRunWithJudgeResult(String runId) {
        return runWithJudgeResultCache.get(runId);
    }

    /**
     * 试运行判题的异步主体（在 {@code runWithJudgeExecutor} 池线程里跑）。
     *
     * <p>与正式 judge 同源：driver 拼装、沙箱执行、复用 VerdictResolver 归约。不写库、不抢租约、
     * 不启心跳。沙箱配额由 {@code SandboxProxy.executeCode} 里的 {@code SandboxConcurrencyLimiter}
     * 统一管控（判题 / 试运行 / SPJ 共享 4 槽位）。</p>
     */
    private JudgeInfo doRunWithJudge(RunJudgeRequest req, Question question) {
        String code = req.getCode();
        String language = req.getLanguage();
        // 核心代码模式：driver（含 Main）由题目 codeTemplate 派生并拼在用户代码之前
        String driverCode = question != null ? CodeTemplateGenerator.deriveDriverCode(question.getCodeTemplate()) : null;
        SandboxCodeAssembler.checkUserCode(code, driverCode);
        String assembled = SandboxCodeAssembler.assemble(code, driverCode);

        // 把前端带来的可编辑用例转成判题侧契约（输入 + 期望输出 + 序号），与正式判题同构
        List<TestCaseData> judgeCases = new ArrayList<>(req.getCases().size());
        for (int i = 0; i < req.getCases().size(); i++) {
            RunCase rc = req.getCases().get(i);
            judgeCases.add(TestCaseData.builder()
                    .index(i)
                    .input(rc.getInput() == null ? "" : rc.getInput())
                    .expectedOutput(rc.getExpectedOutput())
                    .build());
        }
        List<String> inputList = judgeCases.stream().map(TestCaseData::getInput).toList();

        Sandbox sandbox = SandboxFactory.newInstance(codesandboxType);
        SandboxProxy codeSandboxProxy = new SandboxProxy(sandbox, sandboxConcurrencyLimiter);
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .inputList(inputList)
                .code(assembled)
                .language(language)
                .inputMode(ExecuteCodeRequest.INPUT_MODE_FILE)
                .build();
        // 沙箱异常直接上抛：外层 lambda 会捕获并落成 SYSTEM_ERROR 结果
        ExecuteCodeResponse response = codeSandboxProxy.executeCode(executeCodeRequest);
        return doJudgeFromSandboxResponse(response, judgeCases, language, question);
    }

    /**
     * 从沙箱响应构造 JudgeContext 并归约（试运行与正式判题共用的「沙箱响应 → 结论」一段）。
     */
    private JudgeInfo doJudgeFromSandboxResponse(ExecuteCodeResponse response, List<TestCaseData> judgeCases,
                                                 String language, Question question) {
        JudgeContext judgeContext = JudgeContext.builder()
                .judgeCases(judgeCases)
                // 试运行：用例由用户手填，允许不填期望输出 → 保持非严格，走中性态「已执行」
                .requireExpectedOutput(false)
                .judgeConfig(question != null
                        ? QuestionAdminVO.objToVo(question).getJudgeConfig() : defaultJudgeConfig())
                .language(language)
                // 特判程序源码与语言：compareMode=SPJ 时由 VerdictResolver 交给 SpecialJudgeExecutor 执行
                .spjCode(question != null ? question.getSpjCode() : null)
                .spjLanguage(question != null ? question.getSpjLanguage() : null)
                .sandboxCaseResults(response.getCaseResults())
                .compileError(response.getCompileError())
                .systemError(response.getSystemError())
                .sandboxMessage(response.getMessage())
                .build();
        return judgeStrategyManager.doJudge(judgeContext);
    }

    /**
     * 把试运行异常转成一条 SYSTEM_ERROR 结论，供前端轮询拿到错误而非一直「未就绪」超时。
     */
    private JudgeInfo buildSystemErrorInfo(Throwable e) {
        JudgeInfo info = new JudgeInfo();
        info.setMessage(VerdictEnum.SYSTEM_ERROR);
        String msg = e.getMessage();
        info.setDetail(msg == null ? "试运行失败" : msg);
        return info;
    }

    /**
     * 试运行场景的默认判题限制
     *
     * <p>正式判题用题目自带的 {@code judgeConfig}；试运行时调用方未必传，
     * 这里给一份宽松的默认值，保证策略不会因 null 抛异常。</p>
     */
    private com.xly.codeforge.model.dto.question.JudgeConfig defaultJudgeConfig() {
        com.xly.codeforge.model.dto.question.JudgeConfig judgeConfig =
                new com.xly.codeforge.model.dto.question.JudgeConfig();
        judgeConfig.setTimeLimit(DEFAULT_TIME_LIMIT);
        judgeConfig.setMemoryLimit(DEFAULT_MEMORY_LIMIT);
        return judgeConfig;
    }
}
