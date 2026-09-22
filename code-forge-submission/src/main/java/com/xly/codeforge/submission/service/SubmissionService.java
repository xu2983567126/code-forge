package com.xly.codeforge.submission.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.judge.RunJudgeRequest;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.dto.submission.SubmissionCreateRequest;
import com.xly.codeforge.model.dto.submission.SubmissionQueryRequest;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.SubmissionVO;

import java.util.List;
import java.util.Map;

public interface SubmissionService extends IService<Submission> {

    /**
     * 提交题目（落库 + 异步判题）
     *
     * @param submissionCreateRequest 题目提交信息
     * @param loginUser            当前登录用户
     * @return 提交记录 id
     */
    long submit(SubmissionCreateRequest submissionCreateRequest, User loginUser);

    /**
     * 获取查询条件
     *
     * @param submissionQueryRequest 查询请求
     * @return 查询包装类
     */
    QueryWrapper<Submission> getQueryWrapper(SubmissionQueryRequest submissionQueryRequest);

    /**
     * 获取提交记录封装(脱敏)
     *
     * @param submission 提交记录
     * @param loginUser  当前登录用户
     * @return 视图对象
     */
    SubmissionVO getSubmissionVO(Submission submission, User loginUser);

    /**
     * 按 id 获取单条提交记录详情（含权限校验与关联信息填充）
     *
     * <p>读取权限：<b>仅本人与管理员</b>。提交详情带源码与逐用例判题明细，
     * 属个人数据，不做「脱敏后对所有人可见」——只抹掉 code 仍可从用例耗时 /
     * 内存反推他人解法特征。</p>
     *
     * @param id        提交记录 id
     * @param loginUser 当前登录用户（不可为匿名）
     * @return 提交详情；已删除或不存在的记录报 40400，越权报 40101
     */
    SubmissionVO getSubmissionVOById(long id, User loginUser);

    /**
     * 收敛查询的数据范围（在构造 QueryWrapper 之前调用）
     *
     * <p>普通用户的查询条件一律被改写成「user_id = 自己」，且传他人的 userId 直接报
     * 40101；管理员不受限（不传 userId 即全站）。</p>
     *
     * <p>规则集中在此处而非散落各 controller：新增读取入口（导出、搜索）时只要
     * 调一次本方法即继承同一套数据范围，不会漏。</p>
     *
     * @param submissionQueryRequest 查询请求（会被就地改写）
     * @param loginUser              当前登录用户
     */
    void applyDataScope(SubmissionQueryRequest submissionQueryRequest, User loginUser);

    /**
     * 分页获取提交记录封装
     *
     * @param submissionPage 提交记录分页
     * @param loginUser      当前登录用户
     * @return 视图分页
     */
    Page<SubmissionVO> getSubmissionVOPage(Page<Submission> submissionPage, User loginUser);

    /**
     * 试运行判题（异步：立刻返回 runId，结果走轮询端点；不落库、不产生提交记录）
     *
     * <p>与正式提交的区别：本方法<b>不</b>产生 submission、不写数据库，用例由前端带来（可编辑）。
     * 仅做登录 / 限流 / 入参裁剪，判题逻辑全在 judge-service 的 {@code runAndJudge}。</p>
     *
     * @param runJudgeRequest 试运行判题请求（题目 id / 代码 / 语言 / 可编辑用例）
     * @param loginUser       当前登录用户（必须登录）
     * @return 本次试运行的 runId（用于后续轮询结果）
     */
    String runWithJudge(RunJudgeRequest runJudgeRequest, User loginUser);

    /**
     * 取试运行结果（轮询端点数据源）。
     *
     * <p>结果不落库，只在 judge-service 内存缓存短暂停留；未就绪/已过期返回 {@code null}。</p>
     *
     * @param runId 试运行 id
     * @return 聚合判题结论 + 逐用例明细；未就绪/已过期返回 {@code null}
     */
    JudgeInfo getRunWithJudgeResult(String runId);

    /**
     * 获取某用户在某题上的最佳提交
     *
     * <p>「最佳」的排序规则：先取 AC 的（其中取耗时最短），无 AC 则取最近一次提交。
     * 这样前端始终有东西可展示 —— 用户做完一道题，最关心的是「我过了没」，
     * 过了就展示最快的那次，没过就展示最后一次以便回看代码。</p>
     *
     * @param questionId 题目 id
     * @param loginUser  当前登录用户（可为匿名空壳）
     * @return 最佳提交 VO；该用户在这道题上没有任何提交时返回 null
     */
    SubmissionVO getBestSubmission(long questionId, User loginUser);

    /**
     * 批量获取当前用户对多道题的最佳提交（按题分组，供列表页标注「已通过」）
     *
     * @param questionIds 题目 id 集合
     * @param userId      用户 id
     * @return 题目 id → 是否已 AC
     */
    Map<Long, Boolean> mapSolvedQuestions(List<Long> questionIds, Long userId);

    /**
     * 获取所有判题结果枚举（供前端渲染筛选下拉与颜色映射）
     *
     * <p>返回枚举本身而不是硬编码在前端的常量：新增一种 verdict 时只需改后端一处，
     * 前端下拉与颜色映射会自动跟上。</p>
     *
     * @return 每个 verdict 一行：code / text / color
     */
    List<Map<String, String>> listVerdictOptions();

    /**
     * 回填历史数据的 verdict 列
     *
     * <p>历史提交记录的 verdict 列为 NULL，会让按 verdict 筛选与「已通过」统计
     * 漏掉这些数据。本方法从 {@code judge_info} 的 {@code message} 反推 verdict 写入。</p>
     *
     * <p>幂等：只处理 {@code verdict IS NULL} 且 {@code status = 2} 的记录，
     * 重复执行不会覆盖已有值。</p>
     *
     * @return 回填的记录数
     */
    int backfillVerdict();

    /**
     * 判题并发 fencing：抢占租约（CAS）
     *
     * <p>把 WAITING 的提交原子置为 RUNNING 并写入 attemptId + 租约过期时间。
     * 返回 1=抢到；0=已被别人抢走或已非 WAITING（含已终态 / 重复事件）。</p>
     *
     * @param req 含 id / attemptId / generation / ttlSeconds
     * @return 受影响行数（0 或 1）
     */
    int acquireLease(com.xly.codeforge.model.dto.submission.SubmissionFenceRequest req);

    /**
     * 判题并发 fencing：心跳续租
     *
     * <p>仅按 attemptId 续租；返回 0 表示已丢租约（被 reaper 回收重派）。</p>
     *
     * @param req 含 id / attemptId / ttlSeconds
     * @return 受影响行数（0 或 1）
     */
    int renewLease(com.xly.codeforge.model.dto.submission.SubmissionFenceRequest req);

    /**
     * 判题并发 fencing：写回判题结论（SUCCEED）
     *
     * <p>CAS 必须同时匹配 generation + attemptId；返回 0 表示结果已 stale（被重派），丢弃不写。</p>
     *
     * @param req 含 id / generation / attemptId / status / verdict / judgeInfo
     * @return 受影响行数（0 或 1）
     */
    int writeVerdict(com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest req);

    /**
     * 判题并发 fencing：判题失败兜底（FAILED + SYSTEM_ERROR）
     *
     * <p>与 {@link #writeVerdict} 同一条 CAS 路径；返回 0 表示已丢租约，跳过（reaper 会重派）。</p>
     *
     * @param req 含 id / attemptId / generation
     * @return 受影响行数（0 或 1）
     */
    int markFailed(com.xly.codeforge.model.dto.submission.SubmissionFenceRequest req);
}
