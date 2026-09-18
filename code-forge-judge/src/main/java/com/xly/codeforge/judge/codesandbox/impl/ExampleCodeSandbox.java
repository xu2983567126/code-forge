package com.xly.codeforge.judge.codesandbox.impl;

import com.xly.codeforge.judge.codesandbox.CodeSandbox;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.JudgeInfoMessageEnum;
import com.xly.codeforge.model.enums.SubmissionStatusEnum;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;

import java.util.List;

public class ExampleCodeSandbox implements CodeSandbox {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.ACCEPTED.getText());
        judgeInfo.setTime(100L);
        judgeInfo.setMemory(100L);

        ExecuteCodeResponse executeCodeResponse = ExecuteCodeResponse.builder()
                .outputList(executeCodeRequest.getInputList())
                .message("测试执行成功！")
                .status(SubmissionStatusEnum.SUCCEED.getValue())
                .judgeInfoList(List.of(judgeInfo))
                .build();
        return executeCodeResponse;
    }
}
