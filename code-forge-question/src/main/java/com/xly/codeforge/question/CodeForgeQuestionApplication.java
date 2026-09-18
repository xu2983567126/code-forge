package com.xly.codeforge.question;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.xly.codeforge.question.mapper")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan("com.xly")
@EnableFeignClients(basePackages = "com.xly.codeforge.client.service")
public class CodeForgeQuestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeForgeQuestionApplication.class, args);
    }

}
