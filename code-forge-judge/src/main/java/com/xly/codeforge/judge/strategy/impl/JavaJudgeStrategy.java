package com.xly.codeforge.judge.strategy.impl;
import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.judge.strategy.JudgeStrategy;
import com.xly.codeforge.model.dto.question.JudgeConfig;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class JavaJudgeStrategy implements JudgeStrategy {
    @Resource
    private DefaultJudgeStrategy defaultJudgeStrategy;

    private final Long JAVA_TIMEOUT = 100L;

    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        JudgeConfig judgeConfig = judgeContext.getJudgeConfig();
        Long timeLimit = judgeConfig.getTimeLimit() + JAVA_TIMEOUT;
        judgeConfig.setTimeLimit(timeLimit);
        judgeContext.setJudgeConfig(judgeConfig);

        return defaultJudgeStrategy.doJudge(judgeContext);
    }
}
