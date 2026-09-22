package com.xly.codeforge.judge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 试运行（run-with-judge）异步化配置（选项 3）
 *
 * <p>试运行原本在控制器线程里同步调沙箱（~2s），多个用户同时试运行会占满 Tomcat 线程，
 * 连登录/浏览都卡。这里给试运行一条独立线程池：控制器立即返回 runId，池线程去抢沙箱配额、
 * 调沙箱、判题、把结果写进 {@code RunWithJudgeResultCache}，前端轮询拿结果。</p>
 *
 * <p><b>池大小为何等于 4</b>：与沙箱配额（{@link SandboxConcurrencyLimiter}）对齐。池线程数再多，
 * 超出 4 的部分也会在 {@code SandboxProxy.executeCode} 处被信号量挡住排队，不会增加沙箱吞吐，
 * 只增加内存里的排队任务。故 core=max=4。</p>
 *
 * <p><b>拒绝策略 AbortPolicy</b>：试运行不落库，丢了用户自己重跑即可（池满时把结果写成
 * 「试运行繁忙」提示，前端轮询拿到后引导重试），不需要阻塞调用方或无限堆积。</p>
 *
 * @author xuxu
 */
@Configuration
public class RunWithJudgeConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor runWithJudgeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("run-with-judge-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
