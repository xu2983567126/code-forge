package com.xly.codeforge.model.judge;


import com.xly.codeforge.model.dto.submission.JudgeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {

    private List<String> outputList;

    private String message;

    private Integer status;

    private List<JudgeInfo> judgeInfoList;
}
