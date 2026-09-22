package com.xly.codeforge.common.mq;

/**
 * 判题队列拓扑名称常量（M7-1）
 *
 * <p>只放字符串常量，不放 {@code @Configuration}：common 被 user/question 也依赖，
 * 而各服务都 {@code @ComponentScan("com.xly")}，拓扑 Bean 放这里会让不消费 MQ 的服务也建连接。
 * 声明侧见 judge 的 {@code JudgeMqTopology}。</p>
 *
 * <p>主链路 {@code judge.direct} → {@code judge.queue}（key={@code judge.route}）；
 * 消费失败 {@code basicNack(requeue=false)} 进死信 {@code judge.dlx} → {@code judge.dlq}
 * （key={@code judge.dlq.route}）供排查。</p>
 */
public final class JudgeMqConstant {

    public static final String EXCHANGE = "judge.direct";
    public static final String QUEUE = "judge.queue";
    public static final String ROUTE = "judge.route";

    public static final String DLX = "judge.dlx";
    public static final String DLQ = "judge.dlq";
    public static final String DLQ_ROUTE = "judge.dlq.route";

    private JudgeMqConstant() {
    }
}
