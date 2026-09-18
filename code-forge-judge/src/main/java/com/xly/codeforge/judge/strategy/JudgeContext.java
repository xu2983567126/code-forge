package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JudgeContext {

    /**
     * 提交的语言，用于策略
     */
    private String language;

    /**
     * 程序执行的输出
     */
    private List<String> outputList;

    /**
     * 测试用例
     */
    private List<JudgeCase> judgeCases;

    /**
     * 执行的限制信息
     */
    private List<JudgeInfo> judgeInfoList;

    /**
     * 预设的限制
     */
    private JudgeConfig judgeConfig;
}
