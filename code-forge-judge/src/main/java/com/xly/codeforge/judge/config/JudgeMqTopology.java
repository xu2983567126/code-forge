package com.xly.codeforge.judge.config;

import com.xly.codeforge.common.mq.JudgeMqConstant;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 判题队列拓扑声明（M7-1）
 *
 * <p>名称常量在 common 的 {@link JudgeMqConstant}（submission 派发消息时要用）；
 * Bean 声明只放本服务 —— judge 是唯一消费方，由它连上 broker 时通过 {@code RabbitAdmin} 建拓扑。</p>
 *
 * <p>队列/exchange 均为 durable。主队列挂 {@code x-dead-letter} 指向 {@code judge.dlx}，
 * 消费失败 {@code basicNack(requeue=false)} 后进 {@code judge.dlq}；
 * 失败提交在 DB 仍 {@code RUNNING}，由 submission 的 {@code JudgingLeaseReaper} 按租约过期重派，
 * 因此不会无限重投。</p>
 */
@Configuration
public class JudgeMqTopology {

    @Bean
    public DirectExchange judgeExchange() {
        return ExchangeBuilder.directExchange(JudgeMqConstant.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(JudgeMqConstant.QUEUE)
                .withArgument("x-dead-letter-exchange", JudgeMqConstant.DLX)
                .withArgument("x-dead-letter-routing-key", JudgeMqConstant.DLQ_ROUTE)
                .build();
    }

    @Bean
    public Binding judgeBinding() {
        return BindingBuilder.bind(judgeQueue()).to(judgeExchange()).with(JudgeMqConstant.ROUTE);
    }

    @Bean
    public DirectExchange judgeDlx() {
        return ExchangeBuilder.directExchange(JudgeMqConstant.DLX).durable(true).build();
    }

    @Bean
    public Queue judgeDlq() {
        return QueueBuilder.durable(JudgeMqConstant.DLQ).build();
    }

    @Bean
    public Binding judgeDlqBinding() {
        return BindingBuilder.bind(judgeDlq()).to(judgeDlx()).with(JudgeMqConstant.DLQ_ROUTE);
    }

    /**
     * 判题消费者容器工厂（P1 / M7）
     *
     * <p>主队列消费从「单线程」提升到 {@code concurrentConsumers=4}，让慢判题（沙箱执行）不再堵住整条队列；
     * 并发安全由 judge 侧 {@code acquireLease} 的 generation+attemptId 双轴 CAS 保证——
     * 同一 submission 的并发/重复事件只有一个能抢到租约，其余静默丢弃。</p>
     *
     * <p>{@code prefetchCount=8} 让每个消费者通道预取足够消息，配合并发度保证 4 路并行不被饿死；
     * {@code acknowledgeMode=MANUAL} 与消费者 {@code basicAck/basicNack} 语义一致。</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory judgeListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(4);
        factory.setMaxConcurrentConsumers(4);
        factory.setPrefetchCount(8);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
