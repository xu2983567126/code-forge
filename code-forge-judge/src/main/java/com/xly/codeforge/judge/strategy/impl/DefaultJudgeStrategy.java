package com.xly.codeforge.judge.strategy.impl;

import cn.hutool.core.collection.CollUtil;
import com.xly.codeforge.judge.strategy.JudgeContext;
import com.xly.codeforge.judge.strategy.JudgeStrategy;
import com.xly.codeforge.judge.strategy.SpecialJudgeExecutor;
import com.xly.codeforge.judge.strategy.VerdictResolver;
import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.VerdictEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.xly.codeforge.model.enums.VerdictEnum.*;


@Component
public class DefaultJudgeStrategy implements JudgeStrategy {

    /**
     * 事实路径归约器。判题结论（verdict）的单点真相源，详见 {@link VerdictResolver}。
     */
    private final VerdictResolver verdictResolver = new VerdictResolver();

    /**
     * 特判执行器（compareMode=SPJ 时由 {@link VerdictResolver} 调用逐用例比对）。
     * 非 SPJ 题不会被使用；Spring 注入，无参构造（测试）下为 null 不影响非 SPJ 路径。
     */
    @Resource
    private SpecialJudgeExecutor specialJudgeExecutor;

    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        // 事实路径归约器是唯一入口：沙箱必须回传逐用例事实（caseResults），
        // 或带编译失败 / 系统错误标记（这两类响应本来就没有 facts）。
        // 既无事实、也无编译/系统错误标记 = 沙箱链路没给出有效执行结果
        // （沙箱未升级到事实模型、或调用失败）→ 判系统错误，不让判题卡死。
        boolean factPath = CollUtil.isNotEmpty(judgeContext.getSandboxCaseResults())
                || Boolean.TRUE.equals(judgeContext.getCompileError())
                || Boolean.TRUE.equals(judgeContext.getSystemError());
        if (!factPath) {
            JudgeInfo fallback = new JudgeInfo();
            fallback.setDetail(judgeContext.getSandboxMessage());
            return finishWith(fallback, new ArrayList<>(), SYSTEM_ERROR);
        }
        return verdictResolver.resolve(judgeContext, specialJudgeExecutor);
    }

    /**
     * 以给定结论收尾：写入聚合 message 与逐用例明细后返回。
     *
     * <p>失败路径不回填聚合 time/memory —— 只有用例全部通过时总量才有意义。</p>
     */
    private JudgeInfo finishWith(JudgeInfo judgeInfoResult, List<JudgeCaseResult> caseResults,
                                 VerdictEnum status) {
        judgeInfoResult.setMessage(status);
        judgeInfoResult.setCaseResults(caseResults);
        return judgeInfoResult;
    }
}
