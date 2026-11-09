-- Immutable definition or paused seed only. NEVER create admission/live stock/member/token keys here.
-- 0 missing on MATCH; 1 created; 2 identical; -1 conflict; -2 wrong type; -3 expiry; -4 invalid input.
if #KEYS ~= 1 or #ARGV ~= 3 or #ARGV[1] < 2 or #ARGV[1] > 131072 then return -4 end
if ARGV[3] ~= 'INSTALL' and ARGV[3] ~= 'MATCH' then return -4 end
if not string.match(ARGV[2], '^[1-9]%d*$') or #ARGV[2] > 16 then return -4 end
local expires = tonumber(ARGV[2])
if not expires or expires > 9007199254740991 or expires ~= math.floor(expires) then return -4 end
local kind = redis.call('TYPE', KEYS[1]).ok
if kind ~= 'none' and kind ~= 'string' then return -2 end
local existing = redis.call('GET', KEYS[1])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if existing then
    local ttl = redis.call('PTTL', KEYS[1])
    if ttl <= 0 or ttl > 86460000 or now + ttl > expires then return -3 end
    if existing == ARGV[1] then return 2 end
    return -1
end
if ARGV[3] == 'MATCH' then return 0 end
if expires <= now or expires - now > 86460000 then return -3 end
-- A single dataset mutation includes the absolute expiry. No partial hash fields or post-write error branch.
local written = redis.call('SET', KEYS[1], ARGV[1], 'PXAT', ARGV[2], 'NX')
if written then return 1 end
return -1
