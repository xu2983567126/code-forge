package com.xly.codeforge.judge.codesandbox;


import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;

public interface CodeSandbox {
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
