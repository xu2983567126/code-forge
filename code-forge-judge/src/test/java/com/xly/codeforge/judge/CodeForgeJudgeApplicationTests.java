package com.xly.codeforge.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文装配冒烟。
 *
 * <p>测试里必须关掉 Nacos 注册与配置拉取 —— 单测环境没有注册中心，
 * 不关的话本测试在无 Nacos 的机器上必然红。服务治理行为（注册、发现）
 * 由 e2e-test.py 在真实集群上覆盖，这里只验证 Spring 容器能装配起来。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
class CodeForgeJudgeApplicationTests {

    @Test
    void contextLoads() {
    }

}
