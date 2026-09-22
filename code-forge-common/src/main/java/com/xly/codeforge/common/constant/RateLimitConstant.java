package com.xly.codeforge.common.constant;

/**
 * 限流相关常量（纯字符串，不引入 Redis 依赖；各模块自行持有 {@code StringRedisTemplate}）。
 *
 * <p>限流原子性靠一条 Lua 脚本（位置见 {@link #LUA_SCRIPT_LOCATION}）：把 {@code set} + {@code expire} 包在同一个脚本里，
 * 避免两步写之间出现窗口、留下永不过期的 Key。键按时间切片，
 * 窗口滚动后旧键自然过期，实现「每 N 秒最多 M 次」的限流。</p>
 */
public final class RateLimitConstant {

    private RateLimitConstant() {
    }

    /**
     * 时间切片固定窗口限流脚本的类路径位置（{@code common} 模块 resources 下）。
     *
     * <p>脚本内容见该 {@code .lua} 文件本身：KEYS[1] = 时间切片后的计数键；
     * ARGV[1] = 窗口秒数（同时作为键 TTL）；返回当前窗口内的计数（含本次）。</p>
     */
    public static final String LUA_SCRIPT_LOCATION = "lua/rate_limit_window.lua";

    private static final String SUBMIT_KEY_PREFIX = "cf:ratelimit:uid:submit:";

    /**
     * 提交限流键（按用户）。本机 OJ 所有客户端对网关都是 127.0.0.1，
     * 故按 userId 限流才是真正生效的防刷维度，而非按 IP。
     */
    public static String submissionKey(long userId) {
        return SUBMIT_KEY_PREFIX + userId;
    }

    private static final String RUN_KEY_PREFIX = "cf:ratelimit:uid:run:";

    /**
     * 试运行判题限流键（按用户）。
     *
     * <p>与提交限流同源的维度（按 userId）：本机 OJ 所有客户端对网关都是 127.0.0.1，
     * 按 IP 限流对单机环境没有意义。试运行是高频交互（每点一次「运行」就一次），
     * 独立成键避免把提交与试运行挤在同一个窗口里互相影响。</p>
     */
    public static String runKey(long userId) {
        return RUN_KEY_PREFIX + userId;
    }
}
