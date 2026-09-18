package com.xly.codeforge.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户服务启动类
 *
 * <p>{@code @EnableFeignClients} 不可省：dashboard 落在本服务，
 * 需要 Feign 调 question / submission 的 {@code /inner/stats}。
 * 不加这个注解，{@code UserFeignClient} 之类的接口不会被扫描成 Bean，
 * 启动时报 {@code NoSuchBeanDefinitionException}。</p>
 */
@SpringBootApplication
@MapperScan("com.xly.codeforge.user.mapper")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan("com.xly")
@EnableFeignClients(basePackages = "com.xly.codeforge.client.service")
public class CodeForgeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeForgeUserApplication.class, args);
    }

}
