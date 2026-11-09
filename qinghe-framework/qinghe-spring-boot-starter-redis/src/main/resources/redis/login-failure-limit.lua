-- KEYS[1]: 环境前缀+入口/商家/账号摘要。每个请求使用独立ticket，防止迟到完成误改新窗口。
-- ARGV: action, ticket, maxFailures, windowMillis。
local key = KEYS[1]
local action = ARGV[1]
local ticket = ARGV[2]
local limit = tonumber(ARGV[3])
local window = tonumber(ARGV[4])

if action == 'ACQUIRE' then
    local failures = tonumber(redis.call('HGET', key, 'failures') or '0')
    local pending = redis.call('HLEN', key) - redis.call('HEXISTS', key, 'failures')
    if failures + pending >= limit then
        local ttl = redis.call('PTTL', key)
        if ttl < 0 then
            redis.call('PEXPIRE', key, window)
            ttl = window
        end
        return math.max(1, ttl)
    end
    redis.call('HSETNX', key, 'failures', 0)
    redis.call('HSET', key, ticket, 1)
    if redis.call('PTTL', key) < 0 then
        redis.call('PEXPIRE', key, window)
    end
    return 0
end

-- 已过期、已完成或不是本请求的占位，不改动计数。
if redis.call('HEXISTS', key, ticket) == 0 then
    return 0
end
redis.call('HDEL', key, ticket)
if action == 'FAILURE' then
    redis.call('HINCRBY', key, 'failures', 1)
elseif action == 'SUCCESS' then
    redis.call('HSET', key, 'failures', 0)
end
if redis.call('HLEN', key) == 1 and redis.call('HGET', key, 'failures') == '0' then
    redis.call('DEL', key)
end
return 0
