package com.xly.codeforge.judge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 判题 fencing 配置（M1）
 *
 * <p>提供判题心跳用的 {@link ScheduledExecutorService} 与租约 / 心跳常量。
 * 常量值直接对齐 UltiCode {@code LeaseConstants}：TTL=60s、心跳=20s（TTL/3）。</p>
 *
 * <p><b>为什么 TTL 固定 60s 不用调大</b>：TTL 只是「进程崩溃后的恢复延迟」，
 * 判题进行中靠 {@code renewLease} 心跳每 20s 续租，长任务（多用例 / 慢沙箱）不会被误杀。</p>
 *
 * @author xuxu
 */
@Configuration
public class JudgeFenceConfig {

    /**
     * 租约时长（秒）：进程崩溃后，提交在 TTL 内自动被 reaper 回收重派
     */
    public static final int LEASE_TTL_SECONDS = 60;

    /**
     * 心跳间隔（秒）：= TTL/3，保证在租约过期前至少续租两次
     */
    public static final int HEARTBEAT_SECONDS = 20;

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService judgeHeartbeatExecutor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "judge-fence-heartbeat-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                });
        return executor;
    }
}
