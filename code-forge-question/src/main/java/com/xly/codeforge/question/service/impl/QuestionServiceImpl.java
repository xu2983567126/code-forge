package com.xly.codeforge.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.exception.ThrowUtils;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.question.QuestionQueryRequest;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionAdminVO;
import com.xly.codeforge.model.vo.QuestionAdjacentVO;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.question.mapper.QuestionMapper;
import com.xly.codeforge.question.service.QuestionService;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Service
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
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, tags), ErrorCode.PARAMS_ERROR);
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StringUtils.isNotBlank(answer) && answer.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长");
        }
        // 难度必须在白名单内，否则题库筛选会因脏值而查不到
        if (StringUtils.isNotBlank(difficulty) && !VALID_DIFFICULTY.contains(difficulty)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "难度取值非法，仅支持：" + VALID_DIFFICULTY);
        }
        if (StringUtils.isNotBlank(judgeCase) && judgeCase.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题用例过长");
        }
        if (StringUtils.isNotBlank(judgeConfig) && judgeConfig.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题配置过长");
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

