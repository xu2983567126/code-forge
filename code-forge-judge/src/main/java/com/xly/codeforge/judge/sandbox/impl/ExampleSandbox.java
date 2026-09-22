package com.xly.codeforge.judge.sandbox.impl;

import com.xly.codeforge.judge.sandbox.Sandbox;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.judge.SandboxCaseResult;

import java.util.List;

/**
 * 示例沙箱（默认回落实现，仅供本地开发 / 演示）。
 *
 * <p>按事实模型回传：每个输入回显成一条 {@link SandboxCaseResult}（exitCode=0、回显 output），
 * 判题策略据此走事实路径归约 —— 不依赖旧版 judgeInfoList / outputList 兼容格式。</p>
 */
public class ExampleSandbox implements Sandbox {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputs = executeCodeRequest.getInputList();
        List<SandboxCaseResult> caseResults = inputs.stream()
                .map(input -> SandboxCaseResult.builder()
                        .output(input) // 示例沙箱：原样回显输入，让用户看到「代码跑通了」
                        .exitCode(0)
                        .timeMs(100L)
                        .memoryKb(100L)
                        .timedOut(false)
                        .build())
                .toList();
        return ExecuteCodeResponse.builder()
                .caseResults(caseResults)
                .build();
    }
}
