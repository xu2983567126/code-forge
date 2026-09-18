package com.xly.codeforge.judge;

import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.SubmissionVO;

import java.util.List;

public interface JudgeService {

    /**
     * 执行判题（完整链路：读提交记录 → 抢占状态 → 跑沙箱 → 落库 → 回写终态）
     *
     * @param questionSubmitId 提交记录 id
     * @return 判题后的提交记录 VO
     */
    SubmissionVO judge(long questionSubmitId);

    /**
     * 试运行代码（不落库、不同步写状态）
     *
     * <p><b>与 {@link #judge} 的本质区别</b>：本方法只负责「把代码跑起来并拿回结果」，
     * 不读提交记录、不写数据库、不产生判题事件。用于题目详情页的「运行」按钮 ——
     * 用户想用样例输入试一下代码，不该在提交记录里留下一行。</p>
     *
     * <p>返回原始执行结果（输出 + 单用例运行信息），由调用方决定是否再做对比。
     * 这里刻意不返回 {@code JudgeInfo} 的聚合结论：试运行没有判题配置可依据，
     * 「对错」应由前端拿输出与期望值自行比对。</p>
     *
     * @param code      用户代码
     * @param language  编程语言（须为 {@code SubmissionLanguageEnum} 支持的取值）
     * @param inputList 每个测试用例对应的一组输入（通常只有一条：用户填的样例输入）
     * @return 沙箱执行结果
     */
    ExecuteCodeResponse runCode(String code, String language, List<String> inputList);

    /**
     * 对已拿到的执行结果做判题，得到聚合结论
     *
     * <p>供试运行场景复用：拿到 {@link #runCode} 的原始结果后，若调用方提供了期望输出，
     * 可用本方法得到 AC / WA 的结论，而不用把判题策略的逻辑复制一份到别处。</p>
     *
     * @param language  编程语言
     * @param judgeCases 测试用例（含期望输出）
     * @param response  沙箱执行结果
     * @return 聚合后的判题信息
     */
    JudgeInfo doJudge(String language,
                      List<com.xly.codeforge.model.dto.question.JudgeCase> judgeCases,
                      ExecuteCodeResponse response);
}
