package com.xly.codeforge.judge.sandbox;

import com.xly.codeforge.judge.sandbox.impl.ExampleSandbox;
import com.xly.codeforge.judge.sandbox.impl.RemoteSandbox;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SandboxFactory} 的类型选择。
 *
 * <p>工厂是纯静态逻辑，配置里的 {@code sandbox.type} 走到这里变成具体实现。
 * 注意 null / 未识别值**不抛错而是回落 example** —— 配置缺失时服务仍可启动，
 * 这是刻意行为（宽松兜底），改它之前先想清楚会不会让现网启动失败。</p>
 */
class SandboxFactoryTest {

    @Test
    void remote返回远程沙箱实现() {
        assertThat(SandboxFactory.newInstance("remote")).isInstanceOf(RemoteSandbox.class);
    }

    @Test
    void example返回示例沙箱实现() {
        assertThat(SandboxFactory.newInstance("example")).isInstanceOf(ExampleSandbox.class);
    }

    @Test
    void 未识别的值与null回落到示例实现() {
        assertThat(SandboxFactory.newInstance("docker")).isInstanceOf(ExampleSandbox.class);
        assertThat(SandboxFactory.newInstance(null)).isInstanceOf(ExampleSandbox.class);
    }
}
