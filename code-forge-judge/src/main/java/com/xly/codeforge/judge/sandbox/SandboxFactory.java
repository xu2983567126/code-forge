package com.xly.codeforge.judge.sandbox;

import com.xly.codeforge.judge.sandbox.impl.ExampleSandbox;
import com.xly.codeforge.judge.sandbox.impl.RemoteSandbox;

public class SandboxFactory {

    /**
     * 按配置类型创建沙箱客户端。
     *
     * <p>注意 null / 未识别值**不抛错而是回落 example** —— 配置缺失时服务仍可启动，
     * 这是刻意行为（宽松兜底）。</p>
     */
    public static Sandbox newInstance(String type) {
        return switch (type) {
            case "remote" -> new RemoteSandbox();
            case null, default -> new ExampleSandbox();
        };
    }
}
