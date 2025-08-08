---
--- Created by zsq.
--- DateTime: 2025/8/8 18:29
---

-- 参数说明
-- KEYS[1] 是锁的键
-- ARGV[1] 是线程标识（threadId）

--- @diagnostic disable-next-line: undefined-global
local key = KEYS[1]
--- @diagnostic disable-next-line: undefined-global
local threadId = ARGV[1]

-- 获取锁中的线程标识
--- @diagnostic disable-next-line: undefined-global
local cacheValue = redis.call('GET', key)

-- 判断是否一致
if cacheValue == threadId then
    -- 一致，释放锁
    --- @diagnostic disable-next-line: undefined-global
    redis.call('DEL', key)
    return 1 -- 返回 1 表示成功释放锁
else
    return 0 -- 返回 0 表示未释放锁
end