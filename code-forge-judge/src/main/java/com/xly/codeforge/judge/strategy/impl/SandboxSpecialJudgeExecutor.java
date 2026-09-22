package com.xly.codeforge.judge.strategy.impl;

import com.xly.codeforge.judge.config.SandboxConcurrencyLimiter;
import com.xly.codeforge.judge.sandbox.*;
import com.xly.codeforge.judge.strategy.SpecialJudgeExecutor;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.judge.SandboxCaseResult;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.xly.codeforge.model.enums.VerdictEnum.ACCEPTED;
import static com.xly.codeforge.model.enums.VerdictEnum.PRESENTATION_ERROR;
import static com.xly.codeforge.model.enums.VerdictEnum.SYSTEM_ERROR;
import static com.xly.codeforge.model.enums.VerdictEnum.WRONG_ANSWER;

/**
 * 基于通用沙箱的特判执行器。
 *
 * <p>复用现有沙箱（跑 {@code spjCode} 而非用户代码）——前提是沙箱把 {@code fileArgs}
 * 物化为文件、并把<b>文件路径</b>当程序参数传入、且回传 {@code exitCode}（P2 已接）。</p>
 *
 * <p>逐用例调用一次：stdin=测试输入，argv=[标准答案文件路径, 用户输出文件路径]。
 * 传路径而非内容本身，绕开 {@code MAX_ARG_STRLEN}=128KB 单参长度上限。</p>
 * 退出码 0/1/2/其他 → ACCEPTED/WRONG_ANSWER/PRESENTATION_ERROR/SYSTEM_ERROR。</p>
 */
@Component
public class SandboxSpecialJudgeExecutor implements SpecialJudgeExecutor {

    @Value("${sandbox.type}")
    private String codesandboxType;

    @Resource
    private SandboxConcurrencyLimiter limiter;

    @Override
    public VerdictEnum judge(String input, String expected, String userOutput,
                                      String spjCode, String spjLanguage) {
        Sandbox sandbox = SandboxFactory.newInstance(codesandboxType);
        SandboxProxy proxy = new SandboxProxy(sandbox, limiter);
        ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                .code(spjCode)
                .language(spjLanguage)
                .inputList(List.of(input == null ? "" : input))
                .fileArgs(List.of(expected == null ? "" : expected, userOutput == null ? "" : userOutput))
                .build();
        ExecuteCodeResponse response = proxy.executeCode(request);
        List<SandboxCaseResult> caseResults = response.getCaseResults();
        if (caseResults == null || caseResults.isEmpty()) {
            // 沙箱没回有效执行结果（链路故障），归为 checker 配置问题，不怪用户
            return SYSTEM_ERROR;
        }
        SandboxCaseResult fact = caseResults.get(0);
        // 特判程序自身超时/崩溃 = checker 配置错误，不能把锅甩给用户
        if (Boolean.TRUE.equals(fact.getTimedOut())) {
            return SYSTEM_ERROR;
        }
        Integer exitCode = fact.getExitCode();
        if (exitCode == null) {
            return SYSTEM_ERROR;
        }
        return switch (exitCode) {
            case 0 -> ACCEPTED;
            case 1 -> WRONG_ANSWER;
            case 2 -> PRESENTATION_ERROR;
            default -> SYSTEM_ERROR;
        };
    }
}
