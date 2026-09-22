-- 时间切片固定窗口限流：计数与过期在同一脚本内原子完成。
-- 把 set + expire 包在脚本里，避免两步写之间出现窗口、留下永不过期的 Key。
--
-- KEYS[1] = 时间切片后的计数键（由调用方拼好传入）
-- ARGV[1] = 窗口秒数（同时作为键的 TTL）
-- 返回    = 当前窗口内的计数（含本次）

if redis.call('exists', KEYS[1]) == 1 then
  return redis.call('incr', KEYS[1])
else
  redis.call('set', KEYS[1], 1)
  redis.call('expire', KEYS[1], ARGV[1])
  return 1
end
