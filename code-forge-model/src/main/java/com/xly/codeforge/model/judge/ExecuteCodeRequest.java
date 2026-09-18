package com.xly.codeforge.model.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {

    private List<String> inputList;

    private String code;

    private String language;

    private List<String> outputList;

    /**
     * 命令行参数（每个测试用例对应一组参数）
     */
    private List<String> argsList;
}
