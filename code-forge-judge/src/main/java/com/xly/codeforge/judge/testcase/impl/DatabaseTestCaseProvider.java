package com.xly.codeforge.judge.testcase.impl;

import cn.hutool.core.collection.CollUtil;
import com.xly.codeforge.judge.testcase.TestCaseData;
import com.xly.codeforge.judge.testcase.TestCaseProvider;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.vo.QuestionAdminVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 数据库版用例提供者（当前默认实现）。
 *
 * <p>从调用方已取好的 {@link Question} 实体的 {@code judgeCase} 列解析用例，映射为存储无关的
 * {@link TestCaseData}。判题主体不感知这是数据库来源 —— 未来切换对象存储只需新增实现类并替换注入，
 * 判题逻辑与沙箱契约（stdin 文本）均不动。</p>
 *
 * <p>注意：本类不再自行发起 RPC 取题。判题主体（{@code JudgeServiceImpl}）在抢到租约后已经取过一次题目，
 * 直接把该 {@link Question} 传入即可，避免「同题双拉」。</p>
 */
@Service
public class DatabaseTestCaseProvider implements TestCaseProvider {

    @Override
    public List<TestCaseData> getTestCases(Question question) {
        if (question == null) {
            return List.of();
        }
        QuestionAdminVO adminVO = QuestionAdminVO.objToVo(question);
        List<JudgeCase> judgeCases = adminVO.getJudgeCase();
        if (CollUtil.isEmpty(judgeCases)) {
            return List.of();
        }
        return IntStream.range(0, judgeCases.size())
                .mapToObj(i -> {
                    JudgeCase judgeCase = judgeCases.get(i);
                    return TestCaseData.builder()
                            .index(i)
                            .input(judgeCase.getInput())
                            .expectedOutput(judgeCase.getExpectedOutput())
                            .build();
                })
                .toList();
    }
}
