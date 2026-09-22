package com.xly.codeforge.client;

import com.xly.codeforge.client.config.ServiceClientAutoConfiguration;
import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 service-client 作为「库」的自动装配：引入本模块后，
 * {@code META-INF/spring/...AutoConfiguration.imports} 应自动注册 4 个 HttpExchange 代理，
 * 消费方无需任何 {@code @Enable*} 或组件扫描。
 *
 * <p>本测试不连 Nacos/Redis/MySQL（库本身也无这些依赖），仅确认代理 bean 能被创建，
 * 并确为 {@link HttpServiceProxyFactory} 产出的 JDK 动态代理（而非普通实例）。</p>
 */
class ServiceClientWiringTest {

    @Test
    void autoConfigurationRegistersFourHttpExchangeProxies() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(WiringConfig.class)) {
            assertProxy(ctx.getBean(UserFeignClient.class), UserFeignClient.class);
            assertProxy(ctx.getBean(QuestionFeignClient.class), QuestionFeignClient.class);
            assertProxy(ctx.getBean(SubmissionFeignClient.class), SubmissionFeignClient.class);
            assertProxy(ctx.getBean(JudgeFeignClient.class), JudgeFeignClient.class);
        }
    }

    private static <T> void assertProxy(Object bean, Class<T> type) {
        assertThat(bean)
                .as("%s 必须是 HttpExchange 代理 bean", type.getSimpleName())
                .isInstanceOf(type);
        assertThat(Proxy.isProxyClass(bean.getClass()))
                .as("%s 应是由 HttpServiceProxyFactory 创建的 JDK 动态代理", type.getSimpleName())
                .isTrue();
    }

    @Configuration
    @EnableAutoConfiguration
    static class WiringConfig {
        // 仅靠 @EnableAutoConfiguration 读取本模块的 AutoConfiguration.imports，
        // 不显式 @Import，以验证「库自动注册」机制本身。
    }
}
