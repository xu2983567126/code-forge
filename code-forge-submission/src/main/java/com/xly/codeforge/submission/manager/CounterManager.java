package com.xly.codeforge.submission.manager;

import cn.hutool.core.util.StrUtil;
import com.xly.codeforge.common.constant.RateLimitConstant;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 通用限流计数器：基于 Redis + Lua 的时间切片固定窗口计数。
 *
 * <p>复用 submission-service 既有的 {@code StringRedisTemplate}（随 Sa-Token 引入的 Lettuce 客户端），
 * 不引入 Redisson。计数与过期在一条 Lua 脚本内原子完成，避免残留永不过期 Key。</p>
 */
@Component
public class CounterManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // DefaultRedisScript 在构造时就把脚本读成字符串并算出 SHA1，之后走 EVALSHA —— 无运行时读文件开销。
    // 构造期即校验资源存在，打包漏了 .lua 会在这里 fail fast，而不是等第一次限流才炸。
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT =
            RedisScript.of(new ClassPathResource(RateLimitConstant.LUA_SCRIPT_LOCATION), Long.class);

    /**
     * 增加并返回当前时间切片内的计数。
     *
     * @param key           业务键（如 {@code cf:ratelimit:uid:submit:123}）
     * @param windowSeconds 窗口秒数（同时作为键的 TTL）
     * @return 当前计数（含本次）；入参非法时返回 0
     */
    public long incrAndGet(String key, int windowSeconds) {
        if (StrUtil.isBlank(key) || windowSeconds <= 0) {
            return 0;
        }
        long timeFactor = Instant.now().getEpochSecond() / windowSeconds;
        String redisKey = key + ":" + timeFactor;
        Long count = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                Collections.singletonList(redisKey), String.valueOf(windowSeconds));
        return count == null ? 0 : count;
    }

    /**
     * 尝试获取配额：计数未超过上限返回 {@code true}，否则 {@code false}（调用方应抛限流异常）。
     */
    public boolean tryAcquire(String key, int windowSeconds, long limit) {
        return incrAndGet(key, windowSeconds) <= limit;
    }
}
