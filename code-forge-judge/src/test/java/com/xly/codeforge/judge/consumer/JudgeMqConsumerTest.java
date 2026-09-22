package com.xly.codeforge.judge.consumer;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.common.mq.JudgeMessage;
import com.xly.codeforge.judge.metrics.JudgeMetrics;
import com.xly.codeforge.judge.service.JudgeService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 判题消费者 ACK/NACK 分支单测（Mockito，不连 broker、不起 Spring 上下文）：
 * 验证判题成功 {@code basicAck}、失败 {@code basicNack(requeue=false)} 进死信的语义。
 */
@ExtendWith(MockitoExtension.class)
class JudgeMqConsumerTest {

    @Mock
    private JudgeService judgeService;
    @Mock
    private JudgeMetrics judgeMetrics;
    @Mock
    private Channel channel;

    @InjectMocks
    private JudgeMqConsumer consumer;

    private Message message(Long submissionId) {
        return new Message(
                JSONUtil.toJsonStr(new JudgeMessage(submissionId)).getBytes(StandardCharsets.UTF_8),
                new MessageProperties());
    }

    @Test
    void handle_whenJudgeSucceeds_acks() throws IOException {
        consumer.handle(message(123L), channel);
        verify(judgeService).judgeSubmission(123L);
        verify(channel).basicAck(0L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void handle_whenJudgeFails_nacksToDlq() throws IOException {
        doThrow(new RuntimeException("boom")).when(judgeService).judgeSubmission(123L);
        consumer.handle(message(123L), channel);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        // requeue=false → 进 judge.dlq，由 reaper 按租约重派，不无限重投
        verify(channel).basicNack(0L, false, false);
    }
}
