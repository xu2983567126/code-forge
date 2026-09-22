package com.xly.codeforge.common.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 判题任务消息体（M7-1）
 *
 * <p>经 RabbitMQ 在「提交/重派」与「判题消费」之间传递。仅携带提交 id，
 * 其余判题上下文由消费者侧 {@code judgeFeignClient.judge(id)} 现拉，避免消息体膨胀与版本漂移。</p>
 *
 * <p>序列化用 hutool {@code JSONUtil} 转 JSON 字符串（{@code RabbitTemplate} 默认
 * {@code SimpleMessageConverter} 原生处理 {@code String}），刻意不依赖 Jackson，
 * 规避 Boot 4 将 Jackson 换成 {@code tools.jackson} 后 {@code Jackson2JsonMessageConverter}
 * 缺 {@code jackson-databind} 的坑。</p>
 */
@Data
public class JudgeMessage implements Serializable {

    /**
     * 提交 id（submission 表主键）
     */
    private Long submissionId;

    /**
     * 重试次数（预留；MVP 不依赖，失败由 JudgingLeaseReaper 按 DB 租约重派）
     */
    private int retryCount = 0;

    /**
     * 无参构造器：供 hutool {@code JSONUtil.toBean} 反序列化消费消息体。
     */
    public JudgeMessage() {
    }

    /**
     * 便捷构造器：派发方只需提交 id 即可组装消息。
     *
     * @param submissionId 提交表主键
     */
    public JudgeMessage(Long submissionId) {
        this.submissionId = submissionId;
    }
}
