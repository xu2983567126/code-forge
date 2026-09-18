package com.xly.codeforge.judge.codesandbox;

import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSandboxProxy implements CodeSandbox {

    final CodeSandbox codeSandbox;

    public CodeSandboxProxy(CodeSandbox codeSandbox) {
        this.codeSandbox = codeSandbox;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        log.info("executeCodeRequest={}", executeCodeRequest);
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        log.info("executeCodeResponse={}", executeCodeResponse);
        return executeCodeResponse;
    }
}
