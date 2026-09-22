package com.xly.codeforge.judge.metrics;

import com.xly.codeforge.common.mq.JudgeMqConstant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 判题链路可观测指标（P0 / M7）
 *
 * <p>全部走 Micrometer，由 actuator 暴露到 {@code /actuator/metrics} 与 {@code /actuator/prometheus}。</p>
 *
 * <ul>
 *   <li>{@code judge.queue.depth}：主队列 {@code judge.queue} 待消费消息数（gauge）。</li>
 *   <li>{@code judge.dlq.depth}：死信队列 {@code judge.dlq} 堆积数（gauge）。</li>
 *   <li>{@code judge.fence.dropped}：fencing 拦截的重复/过期判题次数（抢租失败 + 被抢占后 stale 丢弃）。</li>
 *   <li>{@code judge.execution.duration}：单次判题执行耗时（含沙箱调用，timer）。</li>
 * </ul>
 *
 * <p>队列深度走 {@code queueDeclarePassive} 被动声明查询，不修改队列；broker 不可达或队列尚未声明时
 * 返回 -1，不中断指标采集。{@link RabbitTemplate} 用 {@link ObjectProvider} 兜底，万一上下文未提供也不影响启动。</p>
 */
@Slf4j
@Component
public class JudgeMetrics {

    private final MeterRegistry registry;
    private final RabbitTemplate rabbitTemplate;

    private final Counter fenceDropped;
    private final Timer judgeTimer;

    public JudgeMetrics(MeterRegistry registry, ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.registry = registry;
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.fenceDropped = Counter.builder("judge.fence.dropped")
                .description("fencing 拦截的重复/过期判题次数（抢租失败或被抢占后 stale 丢弃）")
                .register(registry);
        this.judgeTimer = Timer.builder("judge.execution.duration")
                .description("单次判题执行耗时（含沙箱调用）")
                .register(registry);
    }

    @PostConstruct
    public void registerQueueGauges() {
        Gauge.builder("judge.queue.depth", rabbitTemplate, rt -> queueDepth(rt, JudgeMqConstant.QUEUE))
                .description("judge.queue 待消费消息数")
                .register(registry);
        Gauge.builder("judge.dlq.depth", rabbitTemplate, rt -> queueDepth(rt, JudgeMqConstant.DLQ))
                .description("judge.dlq 死信堆积数")
                .register(registry);
    }

    private double queueDepth(RabbitTemplate rt, String queue) {
        if (rt == null) {
            return -1;
        }
        try {
            Long count = rt.execute(channel -> (long) channel.queueDeclarePassive(queue).getMessageCount());
            return count == null ? -1 : count;
        } catch (Exception e) {
            // 队列尚未声明或 broker 不可达：返回 -1，避免指标采集报错中断
            return -1;
        }
    }

    public void incrementFenceDropped() {
        fenceDropped.increment();
    }

    public Timer.Sample startJudge() {
        return Timer.start(registry);
    }

    public void stopJudge(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(judgeTimer);
        }
    }
}
