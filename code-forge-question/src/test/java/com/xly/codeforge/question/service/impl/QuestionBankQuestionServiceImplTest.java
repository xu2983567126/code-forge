package com.xly.codeforge.question.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.model.entity.QuestionBankQuestion;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.QuestionVO;
import com.xly.codeforge.question.mapper.QuestionBankQuestionMapper;
import com.xly.codeforge.question.mapper.QuestionMapper;
import com.xly.codeforge.question.service.QuestionBankService;
import com.xly.codeforge.question.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 题单-题目关联服务单测
 *
 * <p>只测 {@code pageQuestionsInBank} 的装配逻辑（顺序 / 分页元数据 / 越权 / 脏 id 过滤），
 * 不连库：Mapper 与协作 Service 全部 mock。{@code ServiceImpl.page} 委派给
 * {@code baseMapper.selectPage}，故 mock 该方法即可控制关联页。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionBankQuestionServiceImplTest {

    @Mock
    private QuestionBankQuestionMapper bankQuestionMapper;
    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionService questionService;

    private QuestionBankQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionBankQuestionServiceImpl();
        // ServiceImpl 的 baseMapper 由 MP 在 Bean 创建时注入，这里手工塞
        ReflectionTestUtils.setField(service, "baseMapper", bankQuestionMapper);
        ReflectionTestUtils.setField(service, "questionBankService", questionBankService);
        ReflectionTestUtils.setField(service, "questionMapper", questionMapper);
        ReflectionTestUtils.setField(service, "questionService", questionService);
    }

    /**
     * 关联表按加入时间倒序（orderByDesc(id)），而 selectBatchIds 的返回顺序由 DB 决定。
     * 不按关联顺序还原的话，翻页时题目次序会跳动。
     */
    @Test
    void pageQuestionsInBank_restoresRelationOrder() {
        stubRelationPage(3L, List.of(300L, 200L, 100L));
        // 故意乱序返回，模拟 DB 不保证顺序
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(q(100L), q(300L), q(200L)));
        stubVoPassThrough();

        Page<QuestionVO> page = service.pageQuestionsInBank(1L, 1, 10, mock(User.class));

        assertThat(page.getRecords()).extracting(QuestionVO::getId)
                .containsExactly(300L, 200L, 100L);
        assertThat(page.getTotal()).isEqualTo(3L);
    }

    /**
     * 关联表里指向已删除题目的脏 id 必须过滤掉：留着会渲染出「幽灵题」，点进去 404。
     */
    @Test
    void pageQuestionsInBank_dropsMissingQuestions() {
        stubRelationPage(3L, List.of(300L, 200L, 100L));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(q(300L), q(100L)));
        stubVoPassThrough();

        Page<QuestionVO> page = service.pageQuestionsInBank(1L, 1, 10, mock(User.class));

        assertThat(page.getRecords()).extracting(QuestionVO::getId)
                .containsExactly(300L, 100L);
        // total 仍取关联表的真实条数，过滤只影响本页渲染
        assertThat(page.getTotal()).isEqualTo(3L);
    }

    /**
     * 空题单：不能因为 records 为空就漏掉 total，否则前端拿不到「共 0 条」的判定依据。
     */
    @Test
    void pageQuestionsInBank_emptyBank_keepsTotal() {
        stubRelationPage(0L, List.of());

        Page<QuestionVO> page = service.pageQuestionsInBank(1L, 1, 10, mock(User.class));

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    /**
     * 私有题单对无权限者按「不存在」处理（与题单详情同一口径），不做 403 泄露。
     */
    @Test
    void pageQuestionsInBank_privateBank_throwsNotFound() {
        User user = mock(User.class);
        when(questionBankService.getQuestionBankById(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR));

        assertThatThrownBy(() -> service.pageQuestionsInBank(1L, 1, 10, user))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND_ERROR);
    }

    /**
     * 题单 id 非法直接参数错误，不该带着 0 去查库。
     */
    @Test
    void pageQuestionsInBank_invalidBankId_throwsParamsError() {
        assertThatThrownBy(() -> service.pageQuestionsInBank(0L, 1, 10, mock(User.class)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.PARAMS_ERROR);
    }

    private void stubRelationPage(long total, List<Long> questionIds) {
        Page<QuestionBankQuestion> relationPage = new Page<>(1, 10, total);
        relationPage.setRecords(questionIds.stream().map(id -> {
            QuestionBankQuestion rel = new QuestionBankQuestion();
            rel.setQuestionId(id);
            return rel;
        }).collect(Collectors.toList()));
        when(bankQuestionMapper.selectPage(any(Page.class), any())).thenReturn(relationPage);
    }

    /**
     * VO 装配是 QuestionService 的职责，这里只做 id 透传，便于断言顺序。
     */
    private void stubVoPassThrough() {
        when(questionService.getQuestionVOPage(any(Page.class), any())).thenAnswer(inv -> {
            IPage<Question> src = inv.getArgument(0);
            Page<QuestionVO> out = new Page<>(src.getCurrent(), src.getSize(), src.getTotal());
            out.setRecords(src.getRecords().stream().map(q -> {
                QuestionVO vo = new QuestionVO();
                vo.setId(q.getId());
                return vo;
            }).collect(Collectors.toList()));
            return out;
        });
    }

    private static Question q(long id) {
        Question question = new Question();
        question.setId(id);
        return question;
    }
}
