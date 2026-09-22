package com.xly.codeforge.judge.consumer;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.common.mq.JudgeMqConstant;
import com.rabbitmq.client.Channel;
import com.xly.codeforge.common.mq.JudgeMessage;
import com.xly.codeforge.judge.metrics.JudgeMetrics;
import com.xly.codeforge.judge.service.JudgeService;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 判题消费者（M7-1）
 *
 * <p>手动 ACK：判题成功 {@code basicAck}；判题抛异常 {@code basicNack(requeue=false)} 进死信队列。
 * 失败提交在 DB 仍 {@code RUNNING}，由 {@code JudgingLeaseReaper} 按租约过期重派（走同一主链路再判），
 * 因此不会无限重投，也不丢任务（broker 持久化 + 手动 ACK）。</p>
 *
 * <p>本消费者只负责拉取与 ACK：判题是否真的执行由 judge-service 的租约 CAS 决定
 * （{@code acquireLease} 抢不到即静默丢弃），并发边界不在这一层。</p>
 *
 * <p>消费并发度由 {@code judgeListenerContainerFactory} 控制（concurrentConsumers=4），慢判题不堵队列。</p>
 */
@Slf4j
@Component
public class JudgeMqConsumer {

    @Resource
    private JudgeService judgeService;

    @Resource
    private JudgeMetrics judgeMetrics;

    @RabbitListener(queues = JudgeMqConstant.QUEUE, containerFactory = "judgeListenerContainerFactory")
    public void handle(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        JudgeMessage msg = JSONUtil.toBean(
                new String(message.getBody(), StandardCharsets.UTF_8), JudgeMessage.class);
        Long submissionId = msg.getSubmissionId();
        Timer.Sample sample = judgeMetrics.startJudge();
        try {
            judgeService.judgeSubmission(submissionId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("判题失败入死信，submissionId: {}", submissionId, e);
            // requeue=false → 进 judge.dlq 供排查；DB 行仍 RUNNING，由 reaper 按租约重派，不无限重投
            channel.basicNack(deliveryTag, false, false);
        } finally {
            judgeMetrics.stopJudge(sample);
        }
    }
}
