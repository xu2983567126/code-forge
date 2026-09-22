package com.xly.codeforge.submission.manager;

import com.xly.codeforge.common.constant.RateLimitConstant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流计数器集成测试：直连本机 Redis（127.0.0.1:6379）验证 Lua 脚本的原子计数与过期。
 * Redis 不可达时自动跳过（{@code Assumptions}），避免在非本机环境跑测试时失败。
 */
class CounterManagerLiveRedisTest {

    private static StringRedisTemplate redisTemplate;
    private static boolean reachable;

    @BeforeAll
    static void setUp() {
        reachable = isRedisReachable();
        Assumptions.assumeTrue(reachable, "Redis 未在 127.0.0.1:6379 运行，跳过 live 测试");
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void tearDown() {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisTemplate.keys("cf:ratelimit:*"));
            } catch (Exception ignored) {
            }
            ((LettuceConnectionFactory) redisTemplate.getConnectionFactory()).destroy();
        }
    }

    @Test
    void incrAndGet_increments_monotonic_and_expires() {
        String key = RateLimitConstant.submissionKey(990001L) + ":test:" + System.nanoTime();
        // incrAndGet 内部键 = key + ":" + 时间切片，TTL 要查这个带后缀的键
        long factor = Instant.now().getEpochSecond() / 60;
        String redisKey = key + ":" + factor;
        redisTemplate.delete(redisKey);
        CounterManager mgr = new CounterManager();
        setTemplate(mgr, redisTemplate);

        assertEquals(1, mgr.incrAndGet(key, 60));
        assertEquals(2, mgr.incrAndGet(key, 60));
        assertEquals(3, mgr.incrAndGet(key, 60));

        Long ttl = redisTemplate.getExpire(redisKey);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "TTL 应在 (0,60]，实际=" + ttl);
        redisTemplate.delete(redisKey);
    }

    @Test
    void tryAcquire_limits_within_window() {
        String key = RateLimitConstant.submissionKey(990002L) + ":test:" + System.nanoTime();
        redisTemplate.delete(key);
        CounterManager mgr = new CounterManager();
        setTemplate(mgr, redisTemplate);

        for (int i = 1; i <= 30; i++) {
            assertTrue(mgr.tryAcquire(key, 60, 30), "第 " + i + " 次应允许");
        }
        assertFalse(mgr.tryAcquire(key, 60, 30), "第 31 次应被拒");
        redisTemplate.delete(key);
    }

    private static void setTemplate(CounterManager mgr, StringRedisTemplate tpl) {
        try {
            var f = CounterManager.class.getDeclaredField("stringRedisTemplate");
            f.setAccessible(true);
            f.set(mgr, tpl);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isRedisReachable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 6379), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
