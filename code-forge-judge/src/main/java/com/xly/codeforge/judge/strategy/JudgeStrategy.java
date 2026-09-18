package com.xly.codeforge.judge.strategy;

import com.xly.codeforge.model.dto.submission.JudgeInfo;

/**
 * 判题策略
 */
public interface JudgeStrategy {
    /**
     * 根据题目的执行结果，设置运行信息
     * @param judgeContext
     * @return
     */
    JudgeInfo doJudge(JudgeContext judgeContext);
}
