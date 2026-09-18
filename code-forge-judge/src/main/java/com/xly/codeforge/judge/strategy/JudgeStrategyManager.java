package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class JudgeStrategyManager {

    // Spring 会自动注入所有 JudgeStrategy 的实现，key 为 bean 名称
    @Resource
    private Map<String, JudgeStrategy> strategyMap;

    private JudgeStrategy selectStrategy(String language) {
        // 约定：语言名作为 bean 名称的一部分，例如 "java" -> "javaJudgeStrategy"
        String beanName = language + "JudgeStrategy";
        JudgeStrategy strategy = strategyMap.get(beanName);
        if (strategy == null) {
            strategy = strategyMap.get("defaultJudgeStrategy");
        }
        if (strategy == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无可用判题策略");
        }
        return strategy;
    }

    public JudgeInfo doJudge(JudgeContext judgeContext) {
        return selectStrategy(judgeContext.getLanguage()).doJudge(judgeContext);
    }
}
