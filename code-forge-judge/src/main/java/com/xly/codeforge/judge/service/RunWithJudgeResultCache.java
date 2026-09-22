package com.xly.codeforge.judge.service;

import com.xly.codeforge.model.dto.submission.JudgeInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 试运行结果缓存（选项 3 的轮询数据源）
 *
 * <p>run-with-judge 不落库，结果只在内存里短暂停留供前端轮询。每条结果带 TTL（默认 60s），
 * 过期即视为失效（前端轮询超时引导用户重跑）。</p>
 *
 * <p><b>为什么用内存 map 而非 Redis</b>：judge 单实例（沙箱是单 WSL 实例，cap=4，多 judge 无意义）；
 * 试运行结果本就不持久化，重启即丢可接受。零新依赖，最省事。</p>
 *
 * <p>写入时顺手清一遍过期项，再配合定时 sweep，避免长时间尾堆积。</p>
 *
 * @author xuxu
 */
@Slf4j
@Component
public class RunWithJudgeResultCache {

    /**
     * 结果存活时间（ms）：前端轮询通常几秒内拿到，60s 足够覆盖网络/抖动，过期即失效
     */
    private static final long TTL_MILLIS = 60_000L;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private ScheduledExecutorService sweeper;

    @PostConstruct
    public void init() {
        sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "run-with-judge-cache-sweep");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleAtFixedRate(this::sweep, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (sweeper != null) {
            sweeper.shutdown();
        }
    }

    /**
     * 写入试运行结果（覆盖式，同一 runId 只写一次）
     */
    public void put(String runId, JudgeInfo info) {
        store.put(runId, new Entry(info, System.currentTimeMillis() + TTL_MILLIS));
        sweep();
    }

    /**
     * 取出试运行结果；未就绪或已过期都返回 null（前端据此继续轮询 / 判超时）
     */
    public JudgeInfo get(String runId) {
        Entry entry = store.get(runId);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            store.remove(runId);
            return null;
        }
        return entry.info;
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(en -> en.getValue().expireAt < now);
    }

    private static final class Entry {
        final JudgeInfo info;
        final long expireAt;

        Entry(JudgeInfo info, long expireAt) {
            this.info = info;
            this.expireAt = expireAt;
        }
    }
}
