package com.xly.codeforge.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.questionbank.QuestionBankQueryRequest;
import com.xly.codeforge.model.entity.QuestionBank;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionBankVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 题单服务
 *
 * @author xuxu
 */
public interface QuestionBankService extends IService<QuestionBank> {

    /**
     * 校验题单参数
     *
     * @param questionBank 题单实体（title 为必填，长度受限）
     * @param create       是否为新建：新建时标题不能为空
     */
    void validQuestionBank(QuestionBank questionBank, boolean create);

    /**
     * 根据 id 获取题单，私有题单对无权限者按「不存在」处理
     *
     * <p>返回 404 而非 403 是刻意的：403 等于告诉探测者「这个 id 真实存在，只是你无权看」，
     * 攻击者可以据此遍历出全部私有题单 id。统一 404 则不泄露任何存在性信息。</p>
     *
     * @param id        题单 id
     * @param loginUser 当前登录用户（可为匿名空壳，此时只看得到公开题单）
     * @return 题单实体
     */
    QuestionBank getQuestionBankById(long id, User loginUser);

    /**
     * 获取题单视图（含创建者信息、题目数、已通过数、是否已收藏）
     *
     * @param questionBank 题单实体
     * @param loginUser    当前登录用户
     * @return 视图对象
     */
    QuestionBankVO getQuestionBankVO(QuestionBank questionBank, User loginUser);

    /**
     * 根据 id 获取题单视图
     *
     * @param id        题单 id
     * @param loginUser 当前登录用户
     * @return 视图对象
     */
    QuestionBankVO getQuestionBankVOById(long id, User loginUser);

    /**
     * 分页获取题单视图
     *
     * <p>列表项的题目数 / 已通过数 / 收藏状态均<b>批量</b>查询后组装，
     * 严禁在 stream 里逐条查库 —— 10 条记录会变成 30 次查询。</p>
     *
     * @param questionBankPage 题单分页
     * @param loginUser        当前登录用户
     * @return 视图分页
     */
    Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> questionBankPage, User loginUser);

    /**
     * 获取查询条件
     *
     * @param questionBankQueryRequest 查询请求
     * @return 查询包装类
     */
    QueryWrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest);

    /**
     * 校验当前用户是否可编辑该题单
     *
     * <p>本人或管理员通过；其余抛 404（与读取口径一致，同样不泄露存在性）。</p>
     *
     * @param questionBank 题单实体
     * @param loginUser    当前登录用户
     */
    void checkBankEditAuth(QuestionBank questionBank, User loginUser);

    /**
     * fork（复制）题单
     *
     * <p>把源题单的题目全量复制到新题单，新题单默认<b>私有</b> ——
     * 复制一个公开题单不该自动把它再公开一次（用户还没看过内容）。
     * 源题单的 {@code fork_num} 自增。</p>
     *
     * @param sourceBankId 源题单 id
     * @param loginUser    当前登录用户（成为新题单创建者）
     * @return 新题单 id
     */
    long forkQuestionBank(long sourceBankId, User loginUser);

    /**
     * 批量查询「题目数」映射（供列表组装复用）
     *
     * @param bankIds 题单 id 集合
     * @return 题单 id → 题目数；空集合返回空 Map
     */
    Map<Long, Long> mapQuestionCount(Collection<Long> bankIds);

    /**
     * 批量查询「某用户已通过题目数」映射
     *
     * @param bankIds  题单 id 集合
     * @param userId   用户 id；为 null 时返回空 Map（匿名用户没有通过记录）
     * @return 题单 id → 已通过数
     */
    Map<Long, Long> mapSolvedCount(Collection<Long> bankIds, Long userId);

    /**
     * 批量查询「当前用户已收藏的题单 id」（供列表组装复用）
     *
     * @param bankIds  题单 id 集合
     * @param userId   用户 id；为 null 时返回空集合
     * @return 已收藏的题单 id 集合
     */
    List<Long> listFavouritedBankIds(Collection<Long> bankIds, Long userId);
}
