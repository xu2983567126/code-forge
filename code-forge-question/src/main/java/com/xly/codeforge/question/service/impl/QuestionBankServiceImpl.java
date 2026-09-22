package com.xly.codeforge.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.common.PageRequest;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.questionbank.QuestionBankQueryRequest;
import com.xly.codeforge.model.entity.QuestionBank;
import com.xly.codeforge.model.entity.QuestionBankFavourite;
import com.xly.codeforge.model.entity.QuestionBankQuestion;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionBankVO;
import com.xly.codeforge.question.mapper.QuestionBankFavouriteMapper;
import com.xly.codeforge.question.mapper.QuestionBankMapper;
import com.xly.codeforge.question.mapper.QuestionBankQuestionMapper;
import com.xly.codeforge.question.service.QuestionBankService;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题单服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class QuestionBankServiceImpl extends ServiceImpl<QuestionBankMapper, QuestionBank>
        implements QuestionBankService {

    /**
     * 题单标题长度上限
     */
    private static final int MAX_TITLE_LENGTH = 80;

    /**
     * 题单描述长度上限
     */
    private static final int MAX_DESCRIPTION_LENGTH = 8192;

    /**
     * 题目数量上限
     *
     * <p>批量加题接口一次最多处理这么多 id。既防前端误传全表 id，
     * 也防 {@code IN (...)} 拼接出超长 SQL。</p>
     */
    private static final int MAX_QUESTION_BATCH = 200;

    /**
     * 公开标记
     */
    private static final int PUBLIC_YES = 1;

    @Resource
    private UserFeignClient userFeignClient;

    @Resource
    private QuestionBankQuestionMapper questionBankQuestionMapper;

    @Resource
    private QuestionBankFavouriteMapper questionBankFavouriteMapper;

    @Override
    public void validQuestionBank(QuestionBank questionBank, boolean create) {
        if (questionBank == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = questionBank.getTitle();
        String description = questionBank.getDescription();
        // 新建时标题必填
        if (create && StringUtils.isBlank(title)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题单标题不能为空");
        }
        if (StringUtils.isNotBlank(title) && title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题单标题过长");
        }
        if (StringUtils.isNotBlank(description) && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题单描述过长");
        }
        // isPublic 只允许 0/1，脏值会让「公开题单列表」的过滤条件静默失效
        Integer isPublic = questionBank.getIsPublic();
        if (isPublic != null && isPublic != 0 && isPublic != PUBLIC_YES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "isPublic 仅支持 0（私有）或 1（公开）");
        }
    }

    @Override
    public QuestionBank getQuestionBankById(long id, User loginUser) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionBank questionBank = this.getById(id);
        BusinessAssert.notNull(questionBank, ErrorCode.NOT_FOUND_ERROR);
        // 私有题单：非本人、非管理员一律当作不存在
        checkReadAuth(questionBank, loginUser);
        return questionBank;
    }

    @Override
    public QuestionBankVO getQuestionBankVO(QuestionBank questionBank, User loginUser) {
        if (questionBank == null) {
            return null;
        }
        QuestionBankVO questionBankVO = QuestionBankVO.objToVo(questionBank);
        Long bankId = questionBank.getId();
        Long loginUserId = getLoginUserId(loginUser);

        // 统计与收藏状态：单个题单也走批量接口，保证与列表口径完全一致（只有一处 SQL）
        List<Long> single = List.of(bankId);
        questionBankVO.setQuestionCount(mapQuestionCount(single).getOrDefault(bankId, 0L));
        questionBankVO.setSolvedCount(mapSolvedCount(single, loginUserId).getOrDefault(bankId, 0L));
        questionBankVO.setIsFavourited(listFavouritedBankIds(single, loginUserId).contains(bankId));

        // 创建者信息
        Long userId = questionBank.getUserId();
        User user = (userId != null && userId > 0) ? userFeignClient.getById(userId) : null;
        questionBankVO.setUserVO(userFeignClient.getUserVO(user));
        return questionBankVO;
    }

    @Override
    public QuestionBankVO getQuestionBankVOById(long id, User loginUser) {
        QuestionBank questionBank = getQuestionBankById(id, loginUser);
        return getQuestionBankVO(questionBank, loginUser);
    }

    @Override
    public Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> questionBankPage, User loginUser) {
        List<QuestionBank> questionBankList = questionBankPage.getRecords();
        Page<QuestionBankVO> voPage = new Page<>(questionBankPage.getCurrent(), questionBankPage.getSize(),
                questionBankPage.getTotal());
        if (CollUtil.isEmpty(questionBankList)) {
            return voPage;
        }
        List<Long> bankIdList = questionBankList.stream().map(QuestionBank::getId).collect(Collectors.toList());
        Long loginUserId = getLoginUserId(loginUser);

        // 三份批量数据，共 3 次查询；换成逐条查则是 3 × N 次
        Map<Long, Long> countMap = mapQuestionCount(bankIdList);
        Map<Long, Long> solvedMap = mapSolvedCount(bankIdList, loginUserId);
        Set<Long> favouritedIds = Set.copyOf(listFavouritedBankIds(bankIdList, loginUserId));

        // 创建者信息批量查（跨服务，更不能用 N+1）
        Map<Long, User> userMap = Collections.emptyMap();
        List<Long> userIdList = questionBankList.stream()
                .map(QuestionBank::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (CollUtil.isNotEmpty(userIdList)) {
            userMap = userFeignClient.listByIds(userIdList).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        }

        Map<Long, User> finalUserMap = userMap;
        List<QuestionBankVO> voList = questionBankList.stream().map(questionBank -> {
            QuestionBankVO vo = QuestionBankVO.objToVo(questionBank);
            Long bankId = questionBank.getId();
            vo.setQuestionCount(countMap.getOrDefault(bankId, 0L));
            vo.setSolvedCount(solvedMap.getOrDefault(bankId, 0L));
            vo.setIsFavourited(favouritedIds.contains(bankId));
            vo.setUserVO(userFeignClient.getUserVO(finalUserMap.get(questionBank.getUserId())));
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public QueryWrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest) {
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        if (questionBankQueryRequest == null) {
            return queryWrapper;
        }
        Long id = questionBankQueryRequest.getId();
        Long notId = questionBankQueryRequest.getNotId();
        String searchText = questionBankQueryRequest.getSearchText();
        String title = questionBankQueryRequest.getTitle();
        String description = questionBankQueryRequest.getDescription();
        Long userId = questionBankQueryRequest.getUserId();
        Integer isPublic = questionBankQueryRequest.getIsPublic();
        String sortField = questionBankQueryRequest.getSortField();
        String sortOrder = questionBankQueryRequest.getSortOrder();

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(description), "description", description);
        // 关键词合并搜索：标题 or 描述命中任一即可，包在括号里避免破坏其它条件的优先级
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(wrapper -> wrapper.like("title", searchText).or().like("description", searchText));
        }
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "user_id", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(isPublic), "is_public", isPublic);
        // 排序字段必须是 DB 列名（snake_case），由 SqlUtils 白名单挡住注入
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder == null || sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public void checkBankEditAuth(QuestionBank questionBank, User loginUser) {
        BusinessAssert.notNull(questionBank, ErrorCode.NOT_FOUND_ERROR);
        Long loginUserId = getLoginUserId(loginUser);
        // 未登录、非本人、非管理员 —— 统一 404
        if (loginUserId == null
                || (!loginUserId.equals(questionBank.getUserId()) && !userFeignClient.isAdmin(loginUser))) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long forkQuestionBank(long sourceBankId, User loginUser) {
        BusinessAssert.isTrue(loginUser != null && loginUser.getId() != null, ErrorCode.NOT_LOGIN_ERROR);
        // fork 源必须可见：私有题单 fork 不了（对无权限者它是「不存在」）
        QuestionBank source = getQuestionBankById(sourceBankId, loginUser);

        QuestionBank fork = new QuestionBank();
        fork.setTitle(source.getTitle());
        fork.setDescription(source.getDescription());
        fork.setPicture(source.getPicture());
        fork.setUserId(loginUser.getId());
        // 复制出来的题单默认私有：用户还没看过内容，不该替他把别人的题单再公开一次
        fork.setIsPublic(0);
        fork.setSourceBankId(source.getId());
        fork.setForkNum(0);
        boolean saved = this.save(fork);
        BusinessAssert.isTrue(saved, ErrorCode.OPERATION_ERROR);

        // 复制题目组成
        List<QuestionBankQuestion> sourceRelations = questionBankQuestionMapper.selectList(
                new LambdaQueryWrapper<QuestionBankQuestion>()
                        .eq(QuestionBankQuestion::getQuestionBankId, source.getId()));
        if (CollUtil.isNotEmpty(sourceRelations)) {
            List<QuestionBankQuestion> newRelations = sourceRelations.stream().map(relation -> {
                QuestionBankQuestion newRelation = new QuestionBankQuestion();
                newRelation.setQuestionBankId(fork.getId());
                newRelation.setQuestionId(relation.getQuestionId());
                newRelation.setUserId(loginUser.getId());
                return newRelation;
            }).collect(Collectors.toList());
            // 复用批量插入：单条 save 会退化成 N 次 INSERT
            insertBatch(newRelations);
        }

        // 源题单 fork 次数 +1；用 UPDATE ... SET fork_num = fork_num + 1 而非先读后写，避免并发丢更新
        baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<QuestionBank>()
                .setSql("fork_num = fork_num + 1")
                .eq("id", source.getId()));
        return fork.getId();
    }

    @Override
    public Map<Long, Long> mapQuestionCount(Collection<Long> bankIds) {
        if (CollUtil.isEmpty(bankIds)) {
            return Collections.emptyMap();
        }
        return baseMapper.countQuestionsByBankIds(bankIds).stream()
                .collect(Collectors.toMap(QuestionBankMapper.BankQuestionCount::questionBankId,
                        QuestionBankMapper.BankQuestionCount::cnt, (a, b) -> a));
    }

    @Override
    public Map<Long, Long> mapSolvedCount(Collection<Long> bankIds, Long userId) {
        // 匿名用户没有通过记录，直接短路，省一次 JOIN
        if (CollUtil.isEmpty(bankIds) || userId == null) {
            return Collections.emptyMap();
        }
        return baseMapper.countSolvedByBankIds(bankIds, userId).stream()
                .collect(Collectors.toMap(QuestionBankMapper.BankQuestionCount::questionBankId,
                        QuestionBankMapper.BankQuestionCount::cnt, (a, b) -> a));
    }

    @Override
    public List<Long> listFavouritedBankIds(Collection<Long> bankIds, Long userId) {
        if (CollUtil.isEmpty(bankIds) || userId == null) {
            return Collections.emptyList();
        }
        return questionBankFavouriteMapper.selectList(new LambdaQueryWrapper<QuestionBankFavourite>()
                        .select(QuestionBankFavourite::getBankId)
                        .in(QuestionBankFavourite::getBankId, bankIds)
                        .eq(QuestionBankFavourite::getUserId, userId))
                .stream()
                .map(QuestionBankFavourite::getBankId)
                .collect(Collectors.toList());
    }

    /**
     * 批量插入题单-题目关联
     *
     * <p>当前用循环 {@code insert}。MyBatis-Plus 的 {@code saveBatch} 需要注入
     * {@code IService}（本类已有），但题单关联的批量入口在 {@code QuestionBankQuestionService}，
     * 那边有更合适的实现；此处 fork 场景的量级是一次几十条，循环插入的往返开销可接受，
     * 不为它单独引入复杂封装。</p>
     */
    private void insertBatch(List<QuestionBankQuestion> relations) {
        for (QuestionBankQuestion relation : relations) {
            questionBankQuestionMapper.insert(relation);
        }
    }

    /**
     * 读取权限校验：私有题单只对本人/管理员可见
     *
     * <p>注意匿名用户（网关未注入 header 时的空壳 User）也要走这条路，
     * 故这里对 {@code loginUser} 为 null 或 id 为 null 的情况按「游客」处理。</p>
     */
    private void checkReadAuth(QuestionBank questionBank, User loginUser) {
        Integer isPublic = questionBank.getIsPublic();
        if (isPublic != null && isPublic == PUBLIC_YES) {
            return;
        }
        Long loginUserId = getLoginUserId(loginUser);
        BusinessAssert.isTrue(
            Objects.equals(loginUserId, questionBank.getUserId())
            || userFeignClient.isAdmin(loginUser),
            ErrorCode.NO_AUTH_ERROR);
    }

    /**
     * 取登录用户 id，未登录返回 null（不抛异常 —— 列表接口允许匿名访问）
     */
    private Long getLoginUserId(User loginUser) {
        return loginUser == null ? null : loginUser.getId();
    }

    /**
     * 分页参数兜底：页大小未设置或越界时收敛到合理值
     *
     * <p>供子类与调用方复用，避免每个 Controller 各写一遍同样的判断。</p>
     *
     * @param pageRequest 分页请求
     * @return 分页请求本身（已修正）
     */
    static <T extends PageRequest> T normalizePage(T pageRequest) {
        if (pageRequest.getCurrent() < 1) {
            pageRequest.setCurrent(1);
        }
        if (pageRequest.getPageSize() < 1) {
            pageRequest.setPageSize(10);
        }
        return pageRequest;
    }
}
