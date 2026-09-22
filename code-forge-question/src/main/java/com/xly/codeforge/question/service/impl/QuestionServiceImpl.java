package com.xly.codeforge.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.QuestionSubmissionStatsDTO;
import com.xly.codeforge.model.dto.question.JudgeCase;
import com.xly.codeforge.model.dto.question.QuestionQueryRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionAdminVO;
import com.xly.codeforge.model.vo.QuestionAdjacentVO;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.question.mapper.QuestionMapper;
import com.xly.codeforge.question.service.QuestionService;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 *
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question>
        implements QuestionService {

    /**
     * 合法难度白名单
     * <p>
     * 与 DDL 的列注释、前端下拉选项保持一致。数据库层面是 varchar 无约束，
     * 所以校验必须放在这里，否则脏值会让难度筛选静默查不到数据。
     */
    private static final List<String> VALID_DIFFICULTY = List.of("简单", "中等", "困难");

    @Resource
    private UserFeignClient userFeignClient;

    /**
     * 提交服务：题目域不自持提交计数（{@code submit_num}/{@code accepted_num} 两列已无人维护），
     * 通过率在读取时向数据属主（提交域）实时取。
     */
    @Resource
    private SubmissionFeignClient submissionFeignClient;

    @Override
    public void validQuestion(Question question, boolean create) {
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = question.getTitle();
        String content = question.getContent();
        String tags = question.getTags();
        String answer = question.getAnswer();
        String difficulty = question.getDifficulty();
        String judgeCase = question.getJudgeCase();
        String judgeConfig = question.getJudgeConfig();

        // 创建时，参数不能为空
        if (create) {
            BusinessAssert.notBlank(new String[]{title, content, tags}, ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        // 有参数则校验
        BusinessAssert.isTrue(StringUtils.isNotBlank(title) && title.length() <= 80,
            ErrorCode.PARAMS_ERROR, "标题过长");
        BusinessAssert.isTrue(StringUtils.isNotBlank(content) && content.length() <= 8192,
            ErrorCode.PARAMS_ERROR, "内容过长");
        BusinessAssert.isTrue(StringUtils.isNotBlank(answer) && answer.length() <= 8192,
            ErrorCode.PARAMS_ERROR, "答案过长");
        // 难度必须在白名单内，否则题库筛选会因脏值而查不到
        BusinessAssert.isTrue(StringUtils.isNotBlank(difficulty) && VALID_DIFFICULTY.contains(difficulty),
            ErrorCode.PARAMS_ERROR, "难度取值非法，仅支持：" + VALID_DIFFICULTY);
        BusinessAssert.isTrue(StringUtils.isNotBlank(judgeCase) && judgeCase.length() <= 8192,
            ErrorCode.PARAMS_ERROR, "判题用例过长");
        BusinessAssert.isTrue(StringUtils.isNotBlank(judgeConfig) && judgeConfig.length() <= 8192,
            ErrorCode.PARAMS_ERROR, "判题配置过长");
        validateJudgeCases(judgeCase);
    }

    /**
     * 校验判题用例 JSON：可解析、至少一条、且每条都带期望输出。
     *
     * <p>这里是「字段改名但数据没跟着改」这类事故的唯一前置拦截点：若 JSON 用的是旧 key
     * （如 {@code output}），{@link JudgeCase#getExpectedOutput()} 会反序列化成 {@code null}，
     * 判题时只能落进中性态、最终表现为「状态成功但逐用例未知」。在保存这一步直接拒存，
     * 坏用例就再也进不了库。</p>
     *
     * <p>空值放行：PATCH 更新可能不带 judgeCase，是否必填由创建分支的 title/content/tags 校验负责。</p>
     *
     * @param judgeCase 判题用例 JSON 数组
     * @throws BusinessException 非法 JSON / 空数组 / 存在缺少 expectedOutput 的用例
     */
    private void validateJudgeCases(String judgeCase) {
        if (StringUtils.isBlank(judgeCase)) {
            return;
        }
        List<JudgeCase> cases;
        try {
            cases = JSONUtil.toList(judgeCase, JudgeCase.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题用例不是合法的 JSON 数组");
        }
        BusinessAssert.notEmpty(cases, ErrorCode.PARAMS_ERROR, "判题用例不能为空");
        for (int i = 0; i < cases.size(); i++) {
            JudgeCase one = cases.get(i);
            BusinessAssert.isTrue(one != null && StringUtils.isNotBlank(one.getExpectedOutput()),
                    ErrorCode.PARAMS_ERROR,
                    "第 " + (i + 1) + " 个判题用例缺少期望输出（字段名须为 expectedOutput）");
        }
    }

    @Override
    public QuestionVO getQuestionVOById(long id, User loginUser) {
        Question question = checkAndGetQuestion(id);
        QuestionVO questionVO = getQuestionVO(question, loginUser);
        return questionVO;
    }

    /**
     * 获取查询包装类
     * <br>
     * 用户可能根据哪些参数查询
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        Long id = questionQueryRequest.getId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tags = questionQueryRequest.getTags();
        String answer = questionQueryRequest.getAnswer();
        String difficulty = questionQueryRequest.getDifficulty();
        String searchText = questionQueryRequest.getSearchText();
        Long notId = questionQueryRequest.getNotId();
        Long userId = questionQueryRequest.getUserId();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        // 合并搜索：标题 or 内容 命中任一即可（包在括号里，避免与其它 and 条件混淆优先级）
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(wrapper -> wrapper.like("title", searchText).or().like("content", searchText));
        }
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "user_id", userId);
        queryWrapper.eq("is_delete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionVO getQuestionVO(Question question, User loginUser) {
        QuestionVO questionVO = questionVo(question, loginUser);
        // 关联用户信息
        Long userId = question.getUserId();
        User user = (userId != null && userId > 0) ? userFeignClient.getById(userId) : null;
        questionVO.setUserVO(userFeignClient.getUserVO(user));
        fillSubmissionStats(List.of(questionVO));
        return questionVO;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, User loginUser) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 批量查询用户信息
        List<Long> userIdList = questionList.stream().map(Question::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userFeignClient.listByIds(userIdList).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            QuestionVO questionVO = questionVo(question, loginUser);
            User user = userMap.get(question.getUserId());
            questionVO.setUserVO(userFeignClient.getUserVO(user));
            return questionVO;
        }).collect(Collectors.toList());
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 用提交域的实时统计填充 VO 的 submitNum / acceptedNum。
     *
     * @param questionVOList 本页题目 VO；空列表直接返回
     */
    private void fillSubmissionStats(List<QuestionVO> questionVOList) {
        if (CollUtil.isEmpty(questionVOList)) {
            return;
        }
        List<Long> questionIds = questionVOList.stream()
                .map(QuestionVO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, QuestionSubmissionStatsDTO> statsMap = loadSubmissionStats(questionIds);
        for (QuestionVO questionVO : questionVOList) {
            QuestionSubmissionStatsDTO one = statsMap.get(questionVO.getId());
            questionVO.setSubmitNum(one == null ? 0 : toInt(one.getSubmitCount()));
            questionVO.setAcceptedNum(one == null ? 0 : toInt(one.getAcceptedCount()));
        }
    }

    @Override
    public void fillStatsForEntities(List<Question> questions) {
        if (CollUtil.isEmpty(questions)) {
            return;
        }
        List<Long> questionIds = questions.stream()
                .map(Question::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, QuestionSubmissionStatsDTO> statsMap = loadSubmissionStats(questionIds);
        for (Question question : questions) {
            QuestionSubmissionStatsDTO one = statsMap.get(question.getId());
            question.setSubmitNum(one == null ? 0 : toInt(one.getSubmitCount()));
            question.setAcceptedNum(one == null ? 0 : toInt(one.getAcceptedCount()));
        }
    }

    /**
     * 拉取题目维度的提交统计。
     *
     * <p>为什么题目域不读自己的 {@code submit_num} / {@code accepted_num}：它们在运行期
     * 没有任何更新点（只有造数脚本 INSERT 时写死 0），前端按它们算通过率恒得 0%。
     * 提交统计属于提交域，这里改为<b>一次 RPC 覆盖整批题目</b>（列表页 20 行也只打一次）。</p>
     *
     * <p>提交服务不可用时降级为空映射（调用方按 0 处理，前端本就有 {@code ?? 0} 兜底）：
     * 题库列表是只读浏览页，不该因为增值信息取不到就整页失败，但会打 warn 便于发现。</p>
     *
     * @param questionIds 题目 id 列表（可为空）
     * @return 题目 id → 统计；无数据或调用失败时为空映射
     */
    private Map<Long, QuestionSubmissionStatsDTO> loadSubmissionStats(List<Long> questionIds) {
        if (CollUtil.isEmpty(questionIds)) {
            return Map.of();
        }
        try {
            List<QuestionSubmissionStatsDTO> stats = submissionFeignClient.listStatsByQuestionIds(questionIds);
            return (stats == null ? List.<QuestionSubmissionStatsDTO>of() : stats).stream()
                    .collect(Collectors.toMap(QuestionSubmissionStatsDTO::getQuestionId, s -> s, (a, b) -> a));
        } catch (Exception e) {
            log.warn("拉取题目提交统计失败，通过率降级为 0，questionIds={}", questionIds, e);
            return Map.of();
        }
    }

    /**
     * 统计值拆箱：接口返回 Long，载体字段是 Integer；缺失时按 0 计。
     */
    private int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

    @Override
    public QuestionVO getRandomQuestionVO(Long excludeId, User loginUser) {
        Long randomId = baseMapper.selectRandomQuestionId(excludeId);
        if (randomId == null) {
            // 题库为空，或排除当前题后无剩余 —— 属正常业务场景，返回 null 让调用方提示
            return null;
        }
        Question question = this.getById(randomId);
        if (question == null) {
            // 极小概率：选中后被并发删除。不重试，避免在极端情况下打转
            return null;
        }
        return getQuestionVO(question, loginUser);
    }

    @Override
    public QuestionAdjacentVO getAdjacentQuestion(long id, User loginUser) {
        // 先确认当前题存在，避免对不存在的 id 返回「上一题/下一题」造成误导
        checkAndGetQuestion(id);

        QuestionAdjacentVO result = new QuestionAdjacentVO();
        result.setCurrentId(id);

        Long prevId = baseMapper.selectPrevQuestionId(id);
        Long nextId = baseMapper.selectNextQuestionId(id);

        // 首/末题时对应方向为 null，两个 id 都为空是「题库只有一道题」的正常情况
        result.setPrev(buildNavVO(prevId));
        result.setNext(buildNavVO(nextId));
        return result;
    }

    /**
     * 按 id 组装导航项；id 为 null 或题目已不存在时返回 null
     * <p>
     * 导航项只需 id + title，所以用 selectById 而非走完整的 getQuestionVO
     * —— 后者会额外打一次 Feign 查用户信息，导航栏用不上。
     */
    private QuestionAdjacentVO.QuestionNavVO buildNavVO(Long navId) {
        if (navId == null) {
            return null;
        }
        Question question = this.getById(navId);
        if (question == null) {
            return null;
        }
        QuestionAdjacentVO.QuestionNavVO navVO = new QuestionAdjacentVO.QuestionNavVO();
        navVO.setId(question.getId());
        navVO.setTitle(question.getTitle());
        return navVO;
    }

    /**
     * 根据权限返回问题视图
     * @param question
     * @param loginUser
     * @return
     */
    private QuestionVO questionVo(Question question, User loginUser) {
        // 判断是否可以看到完整信息：作者本人 或 管理员
        QuestionVO questionVO = checkAuthor(question, loginUser) ? QuestionAdminVO.objToVo(question) : QuestionVO.objToVo(question);
        return questionVO;
    }

    private Question checkAndGetQuestion(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = this.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return question;
    }

    private boolean checkAuthor(Question question, User loginUser) {
        return loginUser.getId().equals(question.getUserId()) || userFeignClient.isAdmin(loginUser);
    }
}

