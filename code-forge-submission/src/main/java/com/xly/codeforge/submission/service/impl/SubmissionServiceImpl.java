package com.xly.codeforge.submission.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.judge.RunCodeRequest;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.dto.submission.SubmissionAddRequest;
import com.xly.codeforge.model.dto.submission.SubmissionQueryRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.Submission;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.SubmissionLanguageEnum;
import com.xly.codeforge.model.enums.SubmissionStatusEnum;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.model.vo.RunCodeVO;
import com.xly.codeforge.model.vo.SubmissionVO;
import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.submission.event.JudgeEvent;
import com.xly.codeforge.submission.mapper.SubmissionMapper;
import com.xly.codeforge.submission.service.SubmissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
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
 * @description 针对表【question_submit(题目提交)】的数据库操作Service实现
 * @createDate 2026-04-11 17:58:23
 */
@Service
@Slf4j
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission>
        implements SubmissionService {

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
    private ApplicationEventPublisher eventPublisher;

    /**
     * 提交题目
     *
     * @param submissionAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long submit(SubmissionAddRequest submissionAddRequest, User loginUser) {
        String language = submissionAddRequest.getLanguage();
        SubmissionLanguageEnum languageEnum = SubmissionLanguageEnum.getEnumByValue(language);
        if (languageEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言错误");
        }

        long questionId = submissionAddRequest.getQuestionId();
        // 判断实体是否存在，根据类别获取实体
        Question question = questionFeignClient.getQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已提交题目
        long userId = loginUser.getId();
        // 每个用户串行提交题目
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setQuestionId(questionId);
        submission.setLanguage(language);
        submission.setCode(submissionAddRequest.getCode());
        // 设置初始状态
        submission.setStatus(SubmissionStatusEnum.WAITING.getValue());
        submission.setJudgeInfo("{}");
        boolean save = save(submission);
        if (!save) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据插入失败");
        }
        Long questionSubmitId = submission.getId();
        // 发布判题事件
        eventPublisher.publishEvent(new JudgeEvent(this, questionSubmitId));
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
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
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
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 逻辑删除的记录查不出来，与「不存在」同语义
        Submission submission = this.getById(id);
        ThrowUtils.throwIf(submission == null, ErrorCode.NOT_FOUND_ERROR);
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
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 管理员：不传 userId 即全站，传了即指定用户，原样放行
        if (userFeignClient.isAdmin(loginUser)) {
            return;
        }
        Long requestedUserId = submissionQueryRequest.getUserId();
        ThrowUtils.throwIf(requestedUserId != null && !Objects.equals(requestedUserId, loginUser.getId()),
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
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        if (userFeignClient.isAdmin(loginUser)) {
            return;
        }
        ThrowUtils.throwIf(!Objects.equals(submission.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能查看自己的提交记录");
    }

    @Override
    public Page<SubmissionVO> getSubmissionVOPage(Page<Submission> submissionPage, User loginUser) {

        // 获取分页中的【提交记录列表】
        List<Submission> submissionList = submissionPage.getRecords();

        // 新建一个空的VO分页对象，保持页码、页大小、总条数和原分页一致
        Page<SubmissionVO> qsubmissionVOPage = new Page<>(
                submissionPage.getCurrent(),
                submissionPage.getSize(),
                submissionPage.getTotal()
        );

        // 如果列表为空，直接返回空分页，避免空指针
        if (CollUtil.isEmpty(submissionList)) {
            return qsubmissionVOPage;
        }

        // 把提交记录转换成VO对象
        List<SubmissionVO> submissionVOList = submissionList.stream()
                .map(submission -> getSubmissionVO(submission, loginUser))
                .collect(Collectors.toList());

        // 把转换好的VO列表设置到分页对象中
        qsubmissionVOPage.setRecords(submissionVOList);

        // 返回最终给前端的分页
        return qsubmissionVOPage;
    }

    @Override
    public RunCodeVO runCode(RunCodeRequest runCodeRequest, User loginUser) {
        ThrowUtils.throwIf(runCodeRequest == null, ErrorCode.PARAMS_ERROR);
        // 试运行必须登录：沙箱是有限资源，开放匿名试跑等于给外部一个免登录的代码执行入口
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        String code = runCodeRequest.getCode();
        String language = runCodeRequest.getLanguage();
        ThrowUtils.throwIf(StringUtils.isAnyBlank(code, language), ErrorCode.PARAMS_ERROR, "代码与语言不能为空");
        // 代码长度限制：与正式提交保持一致的口径，防止有人用试运行接口传超大文件拖垮沙箱
        ThrowUtils.throwIf(code.length() > MAX_CODE_LENGTH, ErrorCode.PARAMS_ERROR, "代码过长");

        List<JudgeCase> judgeCases = null;
        Long questionId = runCodeRequest.getQuestionId();
        if (questionId != null && questionId > 0) {
            // 带了题目就校验题目存在 —— 否则用户会以为是自己的代码有问题，
            // 实际是前端传了个已被删除的 questionId
            Question question = questionFeignClient.getQuestionById(questionId);
            ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }

        // 用户自填的样例输入：整段作为一个用例的 stdin
        String input = runCodeRequest.getInput();
        List<String> inputList = List.of(input == null ? "" : input);

        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .inputList(inputList)
                .code(code)
                .language(language)
                .build();
        // 转发给 judge-service —— 沙箱接入代码只存在于那里
        ExecuteCodeResponse response = judgeFeignClient.runCode(executeCodeRequest);
        ThrowUtils.throwIf(response == null, ErrorCode.API_REQUEST_ERROR, "运行失败，判题服务无响应");

        RunCodeVO runCodeVO = new RunCodeVO();
        runCodeVO.setOutputList(response.getOutputList());
        runCodeVO.setStatus(response.getStatus());
        runCodeVO.setMessage(response.getMessage());
        // 试运行不做「对错」判断：用户填的样例输入没有对应的期望输出，
        // 硬判只会得到一堆假 WA。verdict 留给前端拿输出与题目用例自行比对，
        // 或由「提交」走正式判题链路。
        runCodeVO.setJudgeInfo(CollUtil.isEmpty(response.getJudgeInfoList())
                ? null : response.getJudgeInfoList().get(0));
        // 汇总耗时 / 内存：试运行一般是单用例，但接口契约允许将来传多条
        if (CollUtil.isNotEmpty(response.getJudgeInfoList())) {
            runCodeVO.setTime(response.getJudgeInfoList().stream()
                    .map(JudgeInfo::getTime).filter(Objects::nonNull)
                    .reduce(0L, Long::sum));
            runCodeVO.setMemory(response.getJudgeInfoList().stream()
                    .map(JudgeInfo::getMemory).filter(Objects::nonNull)
                    .reduce(0L, Long::sum));
        }
        return runCodeVO;
    }

    @Override
    public SubmissionVO getBestSubmission(long questionId, User loginUser) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
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
}




