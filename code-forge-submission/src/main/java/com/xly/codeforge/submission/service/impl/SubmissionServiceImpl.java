package com.xly.codeforge.submission.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.constant.RateLimitConstant;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.judge.RunJudgeRequest;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.dto.submission.SubmissionCreateRequest;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionQueryRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.SubmissionLanguageEnum;
import com.xly.codeforge.model.enums.SubmissionStatusEnum;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.SandboxCodeAssembler;
import com.xly.codeforge.model.judge.CodeTemplateGenerator;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.model.vo.SubmissionVO;
import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.common.mq.JudgeMqConstant;
import com.xly.codeforge.common.mq.JudgeMessage;
import com.xly.codeforge.submission.manager.CounterManager;
import com.xly.codeforge.submission.mapper.SubmissionMapper;
import com.xly.codeforge.submission.service.SubmissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author user
 * @description 针对表【submission(题目提交)】的数据库操作Service实现
 * @createDate 2026-04-11 17:58:23
 */
@Service
@Slf4j
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    /**
     * 代码长度上限（字符数）
     *
     * <p>试运行与正式提交共用同一口径。没有这个限制时，一个几百 KB 的请求
     * 会在沙箱侧被写成文件再编译，把单次判题拖到几十秒。</p>
     */
    private static final int MAX_CODE_LENGTH = 64 * 1024;

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Resource
    private UserFeignClient userFeignClient;

    @Resource
    private JudgeFeignClient judgeFeignClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private CounterManager counterManager;

    @Value("${code-forge.rate-limit.submission.window-seconds:60}")
    private int rateLimitWindowSeconds;

    @Value("${code-forge.rate-limit.submission.limit:30}")
    private long rateLimitMax;

    @Value("${code-forge.rate-limit.run.window-seconds:60}")
    private int rateLimitRunWindowSeconds;

    @Value("${code-forge.rate-limit.run.limit:30}")
    private long rateLimitRunMax;

    /**
     * 提交题目
     *
     * @param submissionCreateRequest
     * @param loginUser
     * @return
     */
    @Override
    public long submit(SubmissionCreateRequest submissionCreateRequest, User loginUser) {
        String language = submissionCreateRequest.getLanguage();
        SubmissionLanguageEnum languageEnum = SubmissionLanguageEnum.getEnumByValue(language);
        if (languageEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言不能为空");
        }

        long userId = loginUser.getId();
        // 提交频率限流：同一用户每窗口最多 N 次，先于任何远程调用与落库（防刷爆判题）
        boolean submitAllowed = counterManager.tryAcquire(RateLimitConstant.submissionKey(userId), rateLimitWindowSeconds, rateLimitMax);
        BusinessAssert.isTrue(submitAllowed, ErrorCode.TOO_MANY_REQUESTS);

        long questionId = submissionCreateRequest.getQuestionId();
        // 判断实体是否存在，根据类别获取实体
        Question question = questionFeignClient.getQuestionById(questionId);
        BusinessAssert.notNull(question, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        // 核心代码模式：用户只实现指定方法，自带 public class Main / main 视为绕过驱动，提交时即拦。
        // driver 不落库，运行时由 codeTemplate 派生；普通题 codeTemplate 为空则派生为 null 回落。
        SandboxCodeAssembler.checkUserCode(submissionCreateRequest.getCode(), CodeTemplateGenerator.deriveDriverCode(question.getCodeTemplate()));
        // 每个用户串行提交题目
        Submission submission = Submission.builder()
            .language(language)
            .code(submissionCreateRequest.getCode())
            .judgeInfo("{}")
            .status(SubmissionStatusEnum.WAITING.getValue())
            .questionId(questionId)
            .userId(userId)
            .build();
        boolean save = save(submission);
        BusinessAssert.isTrue(save, ErrorCode.OPERATION_ERROR, "题目提交失败");
        Long questionSubmitId = submission.getId();
        // 派发判题任务到 RabbitMQ（M7-1）：手动 ACK + 死信，broker 持久化不丢任务
        rabbitTemplate.convertAndSend(JudgeMqConstant.EXCHANGE, JudgeMqConstant.ROUTE, JSONUtil.toJsonStr(new JudgeMessage(questionSubmitId)));
        return questionSubmitId;
    }

    /**
     * 获取查询包装类
     * <br>
     * 用户可能根据哪些参数查询
     *
     * @param submissionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Submission> getQueryWrapper(SubmissionQueryRequest submissionQueryRequest) {
        QueryWrapper<Submission> queryWrapper = new QueryWrapper<>();
        if (submissionQueryRequest == null) {
            return queryWrapper;
        }
        String language = submissionQueryRequest.getLanguage();
        Long questionId = submissionQueryRequest.getQuestionId();
        Integer status = submissionQueryRequest.getStatus();
        String verdict = submissionQueryRequest.getVerdict();
        List<String> verdicts = submissionQueryRequest.getVerdicts();
        Long userId = submissionQueryRequest.getUserId();
        String sortField = submissionQueryRequest.getSortField();
        String sortOrder = submissionQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(ObjectUtils.isNotEmpty(language), "language", language);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "user_id", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "question_id", questionId);
        queryWrapper.eq(SubmissionStatusEnum.getEnumByValue(status) != null, "status", status);
        // verdict 精确匹配（单值）
        queryWrapper.eq(ObjectUtils.isNotEmpty(verdict), "verdict", verdict);
        // verdict 多选匹配：走 IN，命中 idx_status_verdict 索引
        // 用 CollUtil.isNotEmpty 而非 ObjectUtils：空集合要跳过，否则生成 IN () 语法错误
        queryWrapper.in(CollUtil.isNotEmpty(verdicts), "verdict", verdicts);
        // 逻辑删除条件由实体上的 @TableLogic 自动附加，无需手写
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        return queryWrapper;
    }

    @Override
    public SubmissionVO getSubmissionVO(Submission submission, User loginUser) {
        SubmissionVO submissionVO = SubmissionVO.objToVo(submission);
        // 脱敏：非本人且非管理员抹掉源码。
        // 读取入口都已做权限校验（见 checkReadPermission），这里是纵深防御 ——
        // 将来新增入口若漏了校验，至少不会把源码原样漏出去。
        boolean owner = loginUser != null && Objects.equals(submission.getUserId(), loginUser.getId());
        if (!owner && !userFeignClient.isAdmin(loginUser)) {
            submissionVO.setCode(null);
        }
        return submissionVO;
    }

    @Override
    public SubmissionVO getSubmissionVOById(long id, User loginUser) {
        BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        // 逻辑删除的记录查不出来，与「不存在」同语义
        Submission submission = this.getById(id);
        BusinessAssert.notNull(submission, ErrorCode.NOT_FOUND_ERROR);
        checkReadPermission(submission, loginUser);
        SubmissionVO submissionVO = getSubmissionVO(submission, loginUser);
        // 详情页一次请求要拿全展示所需：题目标题 / 难度 + 提交者昵称。
        // 这两次 Feign 只发生在单条读取；列表接口刻意不填 —— 20 行会放大成 40 次跨服务调用。
        submissionVO.setQuestionVO(QuestionVO.objToVo(questionFeignClient.getQuestionById(submission.getQuestionId())));
        submissionVO.setUserVO(userFeignClient.getUserVO(userFeignClient.getById(submission.getUserId())));
        return submissionVO;
    }

    @Override
    public void applyDataScope(SubmissionQueryRequest submissionQueryRequest, User loginUser) {
        checkLoginUser(loginUser);
        // 管理员：不传 userId 即全站，传了即指定用户，原样放行
        if (userFeignClient.isAdmin(loginUser)) {
            return;
        }
        Long requestedUserId = submissionQueryRequest.getUserId();
        // 不传 userId 是合法请求（收敛到自己），只有显式传了他人 id 才拒绝
        BusinessAssert.isTrue(requestedUserId == null || Objects.equals(requestedUserId, loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能查看自己的提交记录");
        // 不传 userId 也必须落到自己身上 —— 否则查询条件为空会返回全站记录
        submissionQueryRequest.setUserId(loginUser.getId());
    }

    /**
     * 校验登录用户能否读取这条提交记录
     *
     * <p>规则：本人 || 管理员。二者都不是时报 40101，而不是「脱敏后放行」。</p>
     */
    private void checkReadPermission(Submission submission, User loginUser) {
        checkLoginUser(loginUser);
        if (userFeignClient.isAdmin(loginUser)) {
            return;
        }
        BusinessAssert.isTrue(Objects.equals(submission.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "只能查看自己的提交记录");
    }

    @Override
    public Page<SubmissionVO> getSubmissionVOPage(Page<Submission> submissionPage, User loginUser) {

        // 获取分页中的【提交记录列表】
        List<Submission> submissionList = submissionPage.getRecords();

        // 新建一个空的VO分页对象，保持页码、页大小、总条数和原分页一致
        Page<SubmissionVO> qsubmissionVOPage = new Page<>(submissionPage.getCurrent(), submissionPage.getSize(), submissionPage.getTotal());

        // 如果列表为空，直接返回空分页，避免空指针
        if (CollUtil.isEmpty(submissionList)) {
            return qsubmissionVOPage;
        }

        // 把提交记录转换成VO对象。
        // 列表场景做一次裁剪：逐用例明细（input / expectedOutput / output 全文）只在详情接口返回 ——
        // 列表页并不展示它，一页 20 条会把响应撑到几十 KB（大输出题更甚）。
        List<SubmissionVO> submissionVOList = submissionList.stream().map(submission -> trimCaseResults(getSubmissionVO(submission, loginUser))).collect(Collectors.toList());

        // 把转换好的VO列表设置到分页对象中
        qsubmissionVOPage.setRecords(submissionVOList);

        // 返回最终给前端的分页
        return qsubmissionVOPage;
    }

    /**
     * 列表场景裁剪：去掉逐用例明细，只保留聚合结论（message / time / memory）。
     *
     * <p>详情接口（{@code /{id}/vo}）与「我的最优提交」仍返回完整明细，
     * 前端提交详情页据此渲染用例卡片。</p>
     *
     * @param submissionVO 列表项的 VO；为 null 时原样返回
     * @return 就地裁剪后的同一个对象
     */
    private SubmissionVO trimCaseResults(SubmissionVO submissionVO) {
        if (submissionVO != null && submissionVO.getJudgeInfo() != null) {
            submissionVO.getJudgeInfo().setCaseResults(null);
        }
        return submissionVO;
    }

    @Override
    public String runWithJudge(RunJudgeRequest runJudgeRequest, User loginUser) {
        BusinessAssert.notNull(runJudgeRequest, ErrorCode.PARAMS_ERROR);
        // 试运行必须登录：沙箱是有限资源，开放匿名试跑等于给外部一个免登录的代码执行入口
        checkLoginUser(loginUser);
        long userId = loginUser.getId();
        // 试运行是高频交互（每点一次「运行」就一次），独立限流、先于任何远程调用，
        // 避免把提交与试运行挤在同一个窗口里互相影响，也防刷爆沙箱。
        boolean runAllowed = counterManager.tryAcquire(RateLimitConstant.runKey(userId), rateLimitRunWindowSeconds, rateLimitRunMax);
        BusinessAssert.isTrue(runAllowed, ErrorCode.TOO_MANY_REQUESTS);
        String code = runJudgeRequest.getCode();
        BusinessAssert.notBlank(code, ErrorCode.PARAMS_ERROR, "代码不能为空");
        // 代码长度限制：与正式提交保持一致的口径，防止有人用试运行接口传超大文件拖垮沙箱
        BusinessAssert.isTrue(code.length() <= MAX_CODE_LENGTH, ErrorCode.PARAMS_ERROR, "代码过长");
        BusinessAssert.notBlank(runJudgeRequest.getLanguage(), ErrorCode.PARAMS_ERROR, "编程语言不能为空");
        // 用例必填：run-with-judge 的语义就是「带用例判对错」，没有用例等于空跑
        BusinessAssert.notEmpty(runJudgeRequest.getCases(), ErrorCode.PARAMS_ERROR, "用例不能为空");
        // 转发给 judge-service —— 沙箱接入代码与判题逻辑只存在于那里（runAndJudge），
        // 本服务不持有沙箱、也不重复实现比对，仅做登录 / 限流 / 入参裁剪。
        // 异步化（选项 3）：judge 立刻返回 runId，真正跑沙箱在独立线程池异步执行，结果走轮询端点。
        return judgeFeignClient.runWithJudge(runJudgeRequest);
    }

    @Override
    public JudgeInfo getRunWithJudgeResult(String runId) {
        return judgeFeignClient.getRunWithJudgeResult(runId);
    }

    @Override
    public SubmissionVO getBestSubmission(long questionId, User loginUser) {
        BusinessAssert.isTrue(questionId > 0, ErrorCode.PARAMS_ERROR);
        Long userId = loginUser == null ? null : loginUser.getId();
        if (userId == null) {
            // 匿名用户没有提交记录，返回 null 而不是抛未登录 —— 题目详情页对游客也要能打开
            return null;
        }
        // 先找 AC 中耗时最短的；没有 AC 再退回最近一次提交
        Long bestId = baseMapper.selectBestAcceptedId(questionId, userId);
        if (bestId == null) {
            bestId = baseMapper.selectLatestId(questionId, userId);
        }
        if (bestId == null) {
            return null;
        }
        Submission submission = this.getById(bestId);
        if (submission == null) {
            // 极小概率：选中后被并发删除。不重试，返回 null 让前端按「暂无提交」处理
            return null;
        }
        return getSubmissionVO(submission, loginUser);
    }

    @Override
    public Map<Long, Boolean> mapSolvedQuestions(List<Long> questionIds, Long userId) {
        if (CollUtil.isEmpty(questionIds) || userId == null) {
            return Collections.emptyMap();
        }
        // 一次查出所有已 AC 的题目 id，再组装成「题目 id → true/false」的完整映射。
        // 只返回已通过的题目会让调用方分不清「没通过」和「没查到」，故这里补齐 false。
        Set<Long> acceptedIds = Set.copyOf(baseMapper.selectAcceptedQuestionIds(questionIds, userId));
        Map<Long, Boolean> result = new HashMap<>(questionIds.size());
        for (Long questionId : questionIds) {
            result.put(questionId, acceptedIds.contains(questionId));
        }
        return result;
    }

    @Override
    public List<Map<String, String>> listVerdictOptions() {
        // 把枚举转成前端友好的结构。用 LinkedHashMap 保持枚举声明顺序
        // （ACCEPTED 在最前），前端下拉框的顺序就是稳定的，不需要再排序。
        return Arrays.stream(VerdictEnum.values()).map(verdict -> {
            Map<String, String> option = new LinkedHashMap<>(4);
            option.put("value", verdict.getCode());
            option.put("label", verdict.getText());
            option.put("color", verdict.getColor());
            return option;
        }).collect(Collectors.toList());
    }

    @Override
    public int backfillVerdict() {
        int affected = baseMapper.backfillVerdict();
        log.info("verdict 历史数据回填完成，影响 {} 条记录", affected);
        return affected;
    }

    // region 判题并发 fencing（M1）

    @Override
    public int acquireLease(SubmissionFenceRequest req) {
        return baseMapper.acquireLease(req);
    }

    @Override
    public int renewLease(SubmissionFenceRequest req) {
        return baseMapper.renewLease(req);
    }

    @Override
    public int writeVerdict(SubmissionVerdictRequest req) {
        return baseMapper.writeVerdictFenced(req);
    }

    @Override
    public int markFailed(SubmissionFenceRequest req) {
        // 失败兜底同样走 fenced 写回：status=3 + SYSTEM_ERROR，CAS 仍卡 generation/attemptId。
        // 这样「已丢租约（被 reaper 回收重派）」的 worker 不会用失效结果覆盖新一次判题。
        SubmissionVerdictRequest verdict = new SubmissionVerdictRequest();
        verdict.setId(req.getId());
        verdict.setGeneration(req.getGeneration());
        verdict.setAttemptId(req.getAttemptId());
        verdict.setStatus(SubmissionStatusEnum.FAILED.getValue());
        verdict.setVerdict(VerdictEnum.SYSTEM_ERROR.getCode());
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(VerdictEnum.SYSTEM_ERROR);
        verdict.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        return baseMapper.writeVerdictFenced(verdict);
    }

    private void checkLoginUser(User loginUser) {
        BusinessAssert.notNull(loginUser, ErrorCode.NOT_LOGIN_ERROR);
        BusinessAssert.notNull(loginUser.getId(), ErrorCode.NOT_LOGIN_ERROR);
    }
    // endregion
}




