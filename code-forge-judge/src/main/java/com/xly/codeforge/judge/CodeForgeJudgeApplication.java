package com.xly.codeforge.judge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 判题服务启动类。
 *
 * <p>注意：这里**故意不写** {@code @EnableAspectJAutoProxy}。它会在启动时触发 aspectj
 * 类加载，迫使 classpath 必须存在 aspectjweaver；本服务既没有 {@code @Aspect} 切面，
 * 也没有 {@code AopContext} 调用，用不上它，也不因此引入 spring-boot-starter-aop 依赖。</p>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan("com.xly")
public class CodeForgeJudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeForgeJudgeApplication.class, args);
    }

}
