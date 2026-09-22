package com.xly.codeforge.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xly.codeforge.model.entity.QuestionBank;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 题单 Mapper
 *
 * @author xuxu
 */
public interface QuestionBankMapper extends BaseMapper<QuestionBank> {

    /**
     * 统计多个题单各自的题目数量
     *
     * <p><b>为什么必须一次性查多个题单</b>：列表页要显示每个题单的题目数，
     * 若对每个题单单独 count，10 条记录就是 10 次查询（N+1）。
     * 这里用 {@code GROUP BY} 一次拿回全部，Service 侧再按 id 组装。</p>
     *
     * <p><b>为什么用 {@code COUNT(DISTINCT question_id)}</b>：关联表有唯一键
     * {@code uk_bank_question} 保证不重复，理论上普通 COUNT 也对。但去重是零成本的兜底 ——
     * 万一将来约束被去掉、或数据经人工导入污染，统计值不会虚高。</p>
     *
     * <p>⚠️ 原生 SQL 不经过 {@code @TableLogic}，{@code is_delete = 0} 必须显式写。</p>
     *
     * @param bankIds 题单 id 集合（不可为空，调用方需先判空）
     * @return 每个题单一行：{@code [question_bank_id, count]}
     */
    List<BankQuestionCount> countQuestionsByBankIds(@Param("bankIds") Collection<Long> bankIds);

    /**
     * 统计某个用户对多个题单各自已通过的题目数
     *
     * <p>判定「已通过」的依据是 {@code submission.verdict = 'ACCEPTED'}
     * （由判题服务写入，见 {@code VerdictEnum}），而不是 {@code status = 2} ——
     * status 描述的是判题流程是否走完，verdict 才是代码本身的结论。</p>
     *
     * <p><b>必须 {@code COUNT(DISTINCT s.question_id)}</b>：同一道题用户可以提交多次，
     * 不去重会把「提交 5 次都 AC」算成 5 道通过题。这是 OJ 统计最常见的一类错误。</p>
     *
     * <p>{@code s.is_delete = 0} 同样要显式写 —— 原生 JOIN 不走逻辑删除拦截。</p>
     *
     * @param bankIds 题单 id 集合（不可为空）
     * @param userId  用户 id
     * @return 每个题单一行：{@code [question_bank_id, solved_count]}
     */
    List<BankQuestionCount> countSolvedByBankIds(@Param("bankIds") Collection<Long> bankIds,
                                                 @Param("userId") Long userId);

    /**
     * 聚合结果载体（题单 id → 数量）
     *
     * <p>用 record 而不是 Map：MyBatis 映射 record 需要指定构造器参数名，
     * 这里靠 {@code -parameters} 编译参数（pom 已配置）自动对应列别名。
     * 字段名 {@code questionBankId} / {@code cnt} 与 SQL 里的列别名一致。</p>
     */
    record BankQuestionCount(Long questionBankId, Long cnt) {
    }
}
