package com.xly.codeforge.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户服务启动类
 *
 * <p>服务间调用客户端（UserFeignClient 等）由 service-client 库的自动配置
 * {@code ServiceClientAutoConfiguration} 注册为 Bean（经 AutoConfiguration.imports 生效），
 * 不再需要 {@code @EnableFeignClients}：dashboard 落在本服务，调 question / submission 的
 * {@code /inner/stats} 走 HttpExchange + RestClient + 负载均衡。</p>
 */
@SpringBootApplication
@MapperScan("com.xly.codeforge.user.mapper")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan("com.xly")
public class CodeForgeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeForgeUserApplication.class, args);
    }

}
