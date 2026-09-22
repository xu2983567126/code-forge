package com.xly.codeforge.judge.sandbox;

import com.xly.codeforge.judge.config.SandboxConcurrencyLimiter;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SandboxProxy implements Sandbox {

    final Sandbox sandbox;
    final SandboxConcurrencyLimiter limiter;

    public SandboxProxy(Sandbox sandbox, SandboxConcurrencyLimiter limiter) {
        this.sandbox = sandbox;
        this.limiter = limiter;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        log.info("executeCodeRequest={}", executeCodeRequest);
        // 所有沙箱调用（正式判题 / 试运行 / SPJ 特判）共享同一把配额，保证总在途 ≤ 4，
        // 不触发沙箱「并发已达上限」拒绝（否则判题侧会翻译成 SYSTEM_ERROR 假判）。
        // 不可中断获取：判题流程无取消语义，中断只破坏现场。
        limiter.acquireUninterruptibly();
        try {
            ExecuteCodeResponse executeCodeResponse = sandbox.executeCode(executeCodeRequest);
            log.info("executeCodeResponse={}", executeCodeResponse);
            return executeCodeResponse;
        } finally {
            limiter.release();
        }
    }
}
