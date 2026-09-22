package com.xly.codeforge.client.config;

import com.xly.codeforge.client.service.JudgeFeignClient;
import com.xly.codeforge.client.service.QuestionFeignClient;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import com.xly.codeforge.client.service.UserFeignClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 服务间调用客户端装配（替代原 OpenFeign 自动扫描）。
 *
 * <p>原 {@code @FeignClient} 走 Nacos 服务发现；现改用 Spring 原生的 {@code @HttpExchange}
 * + {@code RestClient}，服务发现仍由 spring-cloud-loadbalancer 提供：{@code @LoadBalanced}
 * 的 RestClient.Builder 会把 {@code http://<服务名>} 解析为 Nacos 实例。</p>
 *
 * <p>本类经 {@code META-INF/spring/...AutoConfiguration.imports} 自动注册，
 * 消费方引入 service-client 即生效，无需改动组件扫描或加任何 {@code @Enable*} 注解。</p>
 */
@AutoConfiguration
public class ServiceClientAutoConfiguration {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public UserFeignClient userFeignClient(@LoadBalanced RestClient.Builder builder) {
        return (UserFeignClient) buildProxy(builder, UserFeignClient.class);
    }

    @Bean
    public QuestionFeignClient questionFeignClient(@LoadBalanced RestClient.Builder builder) {
        return (QuestionFeignClient) buildProxy(builder, QuestionFeignClient.class);
    }

    @Bean
    public SubmissionFeignClient submissionFeignClient(@LoadBalanced RestClient.Builder builder) {
        return (SubmissionFeignClient) buildProxy(builder, SubmissionFeignClient.class);
    }

    @Bean
    public JudgeFeignClient judgeFeignClient(@LoadBalanced RestClient.Builder builder) {
        return (JudgeFeignClient) buildProxy(builder, JudgeFeignClient.class);
    }

    private static Object buildProxy(RestClient.Builder builder, Class<?> serviceInterface) {
        try {
            RestClient restClient = builder.build();
            HttpServiceProxyFactory factory =
                    HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
            return factory.createClient(serviceInterface);
        } catch (Exception e) {
            throw new IllegalStateException("创建 HttpExchange 代理失败: " + serviceInterface.getName(), e);
        }
    }
}
