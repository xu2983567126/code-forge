package com.xly.codeforge.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步执行配置
 *
 * <p>submission-service 的 {@code JudgeEventListener#handleJudgeEvent} 标了 {@code @Async}，
 * 其异步线程池由本类提供。</p>
 *
 * @author xuxu
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
