-- 返回0表示准入，正数为距固定窗口结束的毫秒数；拒绝请求不续期、不增加计数。
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local count = tonumber(redis.call('GET', key) or '0')
local ttl = redis.call('PTTL', key)

if count >= limit then
    if ttl < 0 then
        redis.call('PEXPIRE', key, window)
        ttl = window
    end
    return math.max(1, ttl)
end

redis.call('INCR', key)
if ttl < 0 then
    redis.call('PEXPIRE', key, window)
end
return 0
