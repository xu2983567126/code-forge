package com.xly.codeforge.judge.strategy.impl;

import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.judge.strategy.JudgeStrategy;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.JudgeInfoMessageEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.xly.codeforge.model.enums.JudgeInfoMessageEnum.*;


@Component
public class DefaultJudgeStrategy implements JudgeStrategy {
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        List<String> outputList = judgeContext.getOutputList();
        List<JudgeCase> judgeCases = judgeContext.getJudgeCases();
        List<JudgeInfo> judgeInfoList = judgeContext.getJudgeInfoList();
        JudgeConfig judgeConfig = judgeContext.getJudgeConfig();
        JudgeInfo judgeInfoResult = new JudgeInfo();
        // 逐用例明细：聚合结论的同时同步构建，供前端 verdict 卡片 / 性能分布使用
        List<JudgeCaseResult> caseResults = new ArrayList<>();
        Long time = 0L;
        Long memory = 0L;

        for (JudgeInfo judgeInfo : judgeInfoList) {
            JudgeCaseResult caseResult = new JudgeCaseResult();
            caseResult.setTime(judgeInfo.getTime());
            caseResult.setMemory(judgeInfo.getMemory());

            // 1. 先比较解的数量
            if (outputList.size() != judgeCases.size()) {
                caseResult.setStatus(WRONG_ANSWER.getValue());
                caseResults.add(caseResult);
                judgeInfoResult.setCaseResults(caseResults);
                updateJudgeInfo(judgeInfoResult, WRONG_ANSWER);
                return judgeInfoResult;
            }
            // 2. 再逐项比较解
            boolean allMatch = IntStream.range(0, judgeCases.size())
                    .allMatch(i -> judgeCases.get(i).getOutput().equals(outputList.get(i)));
            if (!allMatch) {
                caseResult.setStatus(WRONG_ANSWER.getValue());
                caseResults.add(caseResult);
                judgeInfoResult.setCaseResults(caseResults);
                updateJudgeInfo(judgeInfoResult, WRONG_ANSWER);
                return judgeInfoResult;
            }

            // 3. 判断题目限制
            if (judgeInfo.getMemory() > judgeConfig.getMemoryLimit()) {
                judgeInfo.setMemory(judgeConfig.getMemoryLimit());
                caseResult.setMemory(judgeConfig.getMemoryLimit());
                caseResult.setStatus(MEMORY_LIMIT_EXCEEDED.getValue());
                caseResults.add(caseResult);
                judgeInfoResult.setCaseResults(caseResults);
                updateJudgeInfo(judgeInfoResult, MEMORY_LIMIT_EXCEEDED);
                return judgeInfoResult;
            }
            if (judgeInfo.getTime() > judgeConfig.getTimeLimit()) {
                judgeInfo.setTime(judgeConfig.getTimeLimit());
                caseResult.setTime(judgeConfig.getTimeLimit());
                caseResult.setStatus(TIME_LIMIT_EXCEEDED.getValue());
                caseResults.add(caseResult);
                judgeInfoResult.setCaseResults(caseResults);
                updateJudgeInfo(judgeInfoResult, TIME_LIMIT_EXCEEDED);
                return judgeInfoResult;
            }

            time += judgeInfo.getTime();
            memory += judgeInfo.getMemory();
            caseResult.setStatus(ACCEPTED.getValue());
            caseResults.add(caseResult);
        }

        updateJudgeInfo(judgeInfoResult, ACCEPTED);
        judgeInfoResult.setTime(time);
        judgeInfoResult.setMemory(memory);
        judgeInfoResult.setCaseResults(caseResults);

        return judgeInfoResult;
    }

    private void updateJudgeInfo(JudgeInfo judgeInfo, JudgeInfoMessageEnum judgeInfoMessage) {
        judgeInfo.setMessage(judgeInfoMessage.getValue());
    }
}
