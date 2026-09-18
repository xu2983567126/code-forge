package com.xly.codeforge.judge.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.judge.JudgeService;
import com.xly.codeforge.judge.codesandbox.CodeSandbox;
import com.xly.codeforge.judge.codesandbox.CodeSandboxFactory;
import com.xly.codeforge.judge.codesandbox.CodeSandboxProxy;
import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.judge.strategy.JudgeStrategyManager;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.enums.JudgeInfoMessageEnum;
import com.xly.codeforge.model.enums.SubmissionLanguageEnum;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.QuestionAdminVO;
import com.xly.codeforge.model.vo.SubmissionVO;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

    @Value("${codesandbox.type}")
    private String codesandboxType;

    @Resource
    JudgeStrategyManager judgeStrategyManager;

    @Override
    public SubmissionVO judge(long questionSubmitId) {
        // 1. 根据题目的提交 id 获取题目信息、提交信息
        Submission submission = submissionFeignClient.getSubmissionById(questionSubmitId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交记录不存在！");
        }
        Long questionId = submission.getQuestionId();
        Question question = questionFeignClient.getQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目信息不存在！");
        }
        // 2. 如果不是等待状态，就不要重复判题了
        Integer status = submission.getStatus();
        if (!status.equals(WAITING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }
        // 3. 抢占判题权：把提交从「等待中」置为「判题中」
        Submission submissionUpdate = new Submission();
        submissionUpdate.setStatus(RUNNING.getValue());
        submissionUpdate.setId(questionSubmitId);
        boolean updated = submissionFeignClient.updateSubmissionById(submissionUpdate);
        if (!updated) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }

        // 4. 执行判题主体。从这一刻起提交已是「判题中」，任何异常都必须把它推到终态，
        //    否则该提交会永久停留在「判题中」（历史故障：沙箱不可用 → 提交全部变僵尸记录）。
        try {
            QuestionAdminVO questionAdminVO = QuestionAdminVO.objToVo(question);
            List<JudgeCase> judgeCases = questionAdminVO.getJudgeCase();
            List<String> inputList = judgeCases.stream()
                    .map(JudgeCase::getInput)
                    .collect(Collectors.toList());

            String language = submission.getLanguage();
            CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(codesandboxType);
            CodeSandboxProxy codeSandboxProxy = new CodeSandboxProxy(codeSandbox);
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .inputList(inputList)
                    .code(submission.getCode())
                    .language(language)
                    .build();
            ExecuteCodeResponse executeCodeResponse = codeSandboxProxy.executeCode(executeCodeRequest);

            // 5. 根据题目的执行结果，设置运行信息
            JudgeContext judgeContext = JudgeContext.builder()
                    .outputList(executeCodeResponse.getOutputList())
                    .judgeCases(judgeCases)
                    .judgeInfoList(executeCodeResponse.getJudgeInfoList())
                    .judgeConfig(questionAdminVO.getJudgeConfig())
                    .language(language)
                    .build();
            JudgeInfo judgeInfo = judgeStrategyManager.doJudge(judgeContext);
            submissionUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
            submissionUpdate.setStatus(SUCCEED.getValue());
            // 把判题结论从 judgeInfo 里抽出，单独落 verdict 列（供筛选 / 统计走索引）
            submissionUpdate.setVerdict(VerdictEnum.fromJudgeMessage(judgeInfo.getMessage()).getCode());
            boolean update = submissionFeignClient.updateSubmissionById(submissionUpdate);
            if (!update) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误！");
            }
        } catch (Exception e) {
            // 【兜底】判题链路异常（沙箱不可用 / 调用超时 / 策略异常）→ 回写 FAILED，
            // 保证提交不会永远卡在「判题中」。
            log.error("判题异常，submissionId={}，已回写为 FAILED", questionSubmitId, e);
            markFailed(questionSubmitId);
            // 上抛保留错误可见性：调用方 JudgeEventListener 已捕获并仅记日志，不会引发重试。
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题失败：" + e.getMessage());
        }

        Submission submit = submissionFeignClient.getSubmissionById(questionSubmitId);
        return SubmissionVO.objToVo(submit);
    }

    /**
     * 判题失败兜底：把提交置为 FAILED(3) 并写入「系统错误」判题信息。
     *
     * <p>本方法自身吞掉异常，避免状态回写失败掩盖原始判题异常。</p>
     *
     * @param questionSubmitId 提交记录 id
     */
    private void markFailed(long questionSubmitId) {
        try {
            Submission failUpdate = new Submission();
            failUpdate.setId(questionSubmitId);
            failUpdate.setStatus(FAILED.getValue());
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
            failUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
            // 兜底路径同样要写 verdict，否则该提交在按 verdict 筛选时会「消失」
            failUpdate.setVerdict(VerdictEnum.SYSTEM_ERROR.getCode());
            submissionFeignClient.updateSubmissionById(failUpdate);
        } catch (Exception e) {
            log.error("判题失败状态回写异常，submissionId={}", questionSubmitId, e);
        }
    }

    @Override
    public ExecuteCodeResponse runCode(String code, String language, List<String> inputList) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(language)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码与语言不能为空");
        }
        // 语言必须合法：沙箱侧按语言选择编译/运行命令，脏值会以系统错误的形式暴露成 500，
        // 在这里挡掉能给出更准确的 400 提示
        if (SubmissionLanguageEnum.getEnumByValue(language) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言错误");
        }
        // 试运行的输入来源有两种：用户自填的样例输入（1 条），或题目自带的用例（可能多条）。
        // 空列表要补一个空串 —— 沙箱约定「inputList 至少一项」，传空列表会拿不到任何输出。
        List<String> effectiveInputs = CollUtil.isEmpty(inputList) ? List.of("") : inputList;

        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(codesandboxType);
        CodeSandboxProxy codeSandboxProxy = new CodeSandboxProxy(codeSandbox);
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .inputList(effectiveInputs)
                .code(code)
                .language(language)
                .build();
        // 沙箱异常不做兜底回写 —— 试运行没有数据库记录需要保护，
        // 直接上抛让用户看到真实错误，比吞掉后返回一个空结果更有用。
        return codeSandboxProxy.executeCode(executeCodeRequest);
    }

    @Override
    public JudgeInfo doJudge(String language, List<JudgeCase> judgeCases, ExecuteCodeResponse response) {
        if (response == null || CollUtil.isEmpty(response.getJudgeInfoList())) {
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
            return judgeInfo;
        }
        JudgeContext judgeContext = JudgeContext.builder()
                .outputList(response.getOutputList())
                .judgeCases(judgeCases)
                // 试运行没有题目判题配置，用默认限制兜底（时间 1000ms / 内存 100MB），
                // 否则策略里的 timeLimit 比较会 NPE
                .judgeConfig(defaultJudgeConfig())
                .judgeInfoList(response.getJudgeInfoList())
                .language(language)
                .build();
        return judgeStrategyManager.doJudge(judgeContext);
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
