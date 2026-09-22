package com.xly.codeforge.submission.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提交侧可观测指标（P0 / M7）
 *
 * <p>reaper 重派计数：{@code judge.reaper.redispatch} 记录 {@code JudgingLeaseReaper} 每轮实际重派的卡死提交数，
 * 是判题链路「自愈」活跃度的一手信号——数值持续 >0 说明 judge 存在瞬态不可用 / 长任务被回收。</p>
 */
@Slf4j
@Component
public class SubmissionMetrics {

    private final Counter reaperRedispatch;

    public SubmissionMetrics(MeterRegistry registry) {
        this.reaperRedispatch = Counter.builder("judge.reaper.redispatch")
                .description("reaper 每轮实际重派的卡死提交数")
                .register(registry);
    }

    public void incrementReaperRedispatch(long n) {
        if (n > 0) {
            reaperRedispatch.increment(n);
        }
    }
}
