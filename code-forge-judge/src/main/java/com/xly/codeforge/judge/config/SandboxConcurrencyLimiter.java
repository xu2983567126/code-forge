package com.xly.codeforge.judge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * 沙箱并发配额（唯一真相源）
 *
 * <p>所有经 {@code SandboxProxy.executeCode} 的沙箱调用（正式判题 / 试运行 / SPJ 特判）
 * 共享这一把信号量。默认 4，与沙箱侧「并发已达上限 4」对齐 —— 保证总在途沙箱调用 ≤ 4，
 * 不触发沙箱拒绝（否则判题侧会翻译成 {@code SYSTEM_ERROR} 假判）。</p>
 *
 * <p><b>为什么是公平信号量</b>：判题走 MQ 消费者（并发 4），试运行走独立线程池，两条路径抢同一份
 * 4 配额。{@code fair=true} 保证 FIFO，避免试运行在判题高峰被饿死（用户点「运行」要等很久才有响应）。</p>
 *
 * <p>调沙箱上限（WSL {@code code-forge-sandbox} 的并发限制）时，必须同步改本 Bean 的
 * {@code judge.sandbox.concurrency}，否则两边不一致会重新出现「沙箱繁忙」拒绝。</p>
 *
 * @author xuxu
 */
@Component
public class SandboxConcurrencyLimiter {

    private final Semaphore semaphore;

    public SandboxConcurrencyLimiter(@Value("${judge.sandbox.concurrency:4}") int permits) {
        // fair=true：FIFO，试运行不被判题高峰饿死
        this.semaphore = new Semaphore(permits, true);
    }

    /**
     * 获取一个沙箱并发配额。配额耗尽时阻塞（FIFO），不失败、不拒绝。
     *
     * @throws InterruptedException 等待期间线程被中断
     */
    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    /**
     * 获取一个沙箱并发配额（不可中断版）。用于 {@code SandboxProxy.executeCode}
     * 这种「阻塞等配额、拿到就立刻用」的场景：判题流程没有取消语义，中断只会破坏现场，
     * 故用不可中断获取，内部仍保留中断标记。
     */
    public void acquireUninterruptibly() {
        semaphore.acquireUninterruptibly();
    }

    /**
     * 归还一个沙箱并发配额（必须与 {@link #acquire()} 配对，置于 finally）
     */
    public void release() {
        semaphore.release();
    }

    /**
     * 当前可用配额数（可观测 / 调试用）
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
