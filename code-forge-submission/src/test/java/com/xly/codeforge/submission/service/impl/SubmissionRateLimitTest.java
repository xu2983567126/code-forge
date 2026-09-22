package com.xly.codeforge.submission.service.impl;

import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.dto.submission.SubmissionCreateRequest;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.submission.manager.CounterManager;
import com.xly.codeforge.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交限流分支单测（Mockito，不启动 Spring 上下文）：验证超限时抛 {@code TOO_MANY_REQUESTS}，
 * 且限流检查在题目查询/落库/派发判题之前（失败快速）。
 */
@ExtendWith(MockitoExtension.class)
class SubmissionRateLimitTest {

    @Mock
    private CounterManager counterManager;
    @Mock
    private QuestionFeignClient questionFeignClient;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private JudgeFeignClient judgeFeignClient;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private SubmissionMapper submissionMapper;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    @Test
    void submit_whenRateLimited_throwsTooManyRequests() {
        SubmissionCreateRequest req = new SubmissionCreateRequest();
        req.setQuestionId(1L);
        req.setLanguage("java");
        User user = new User();
        user.setId(42L);

        when(counterManager.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> submissionService.submit(req, user));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getCode());

        // 限流检查在前，不应触发题目查询、落库或派发判题
        verify(counterManager).tryAcquire(anyString(), anyInt(), anyLong());
        verify(questionFeignClient, never()).getQuestionById(anyLong());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
