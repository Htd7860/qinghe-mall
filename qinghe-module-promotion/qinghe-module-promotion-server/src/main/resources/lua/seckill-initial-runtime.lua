-- Initial PAUSED runtime only. OPEN/READY is deliberately absent.
-- Keys: immutable seed, distinct runtime hash (same hash tag).
-- Args: mode, token, deadline, digest, end, skuCount, expectedSeed, start, singleLimit, memberLimit,
--       sorted (skuId, quantity, priceCent)*.
-- 0 MATCH miss, 1 created, 2 exact paused match, negatives reject without writes.
local function uint(v, low, high)
    if not v or #v > 16 or not string.match(v, '^%d+$') then return nil end
    if #v > 1 and string.sub(v, 1, 1) == '0' then return nil end
    local n = tonumber(v)
    if not n or n ~= math.floor(n) or n < low or n > high then return nil end
    return n
end
if #KEYS ~= 2 or #ARGV < 13 or (ARGV[1] ~= 'INSTALL' and ARGV[1] ~= 'MATCH') then return -4 end
local deadline = uint(ARGV[3], 1, 4102444799999)
local finish = uint(ARGV[5], 1, 4102444799999)
local count = uint(ARGV[6], 1, 200)
local start = uint(ARGV[8], 946684800000, 4102444799999)
local single = uint(ARGV[9], 1, 9999)
local member = uint(ARGV[10], 1, 2147483647)
if not deadline or not finish or not count or not start or not single or not member
    or start >= finish or deadline > finish or single > member or #ARGV ~= 10 + 3 * count then return -4 end
if #ARGV[2] ~= 36 or not string.match(ARGV[2], '^[0-9a-f%-]+$')
    or #ARGV[4] ~= 64 or not string.match(ARGV[4], '^[0-9a-f]+$') or #ARGV[7] > 131072 then return -4 end
local pairs = {'schema','1','phase','PAUSED','epoch','1','initToken',ARGV[2],
    'initDeadline',ARGV[3],'definitionDigest',ARGV[4],'startAt',ARGV[8],'endAt',ARGV[5],
    'singleLimit',ARGV[9],'memberLimit',ARGV[10],'skuCount',ARGV[6],'memberCount','0','requestCount','0'}
local previous = 0
local total = 0
for i = 1, count do
    local base = 10 + (i - 1) * 3
    local id = uint(ARGV[base + 1], 1, 9007199254740991)
    local quantity = uint(ARGV[base + 2], 1, 2147483647)
    local price = uint(ARGV[base + 3], 1, 2000000000)
    if not id or not quantity or not price or id <= previous then return -4 end
    previous = id
    total = total + quantity
    if total > 2147483647 then return -4 end
    local prefix = 'sku:' .. ARGV[base + 1] .. ':'
    local values = {'allocated',ARGV[base + 2],'available',ARGV[base + 2],
        'held','0','sold','0','returned','0','price',ARGV[base + 3]}
    for j = 1, #values, 2 do
        pairs[#pairs + 1] = prefix .. values[j]
        pairs[#pairs + 1] = values[j + 1]
    end
end
local kind = redis.call('TYPE', KEYS[2]).ok
if kind ~= 'none' and kind ~= 'hash' then return -2 end
if kind == 'hash' then
    if redis.call('HLEN', KEYS[2]) ~= #pairs / 2 or redis.call('PTTL', KEYS[2]) ~= -1 then return -1 end
    for i = 1, #pairs, 2 do
        if redis.call('HGET', KEYS[2], pairs[i]) ~= pairs[i + 1] then return -1 end
    end
    return 2 -- Never restore changed balances or extend the original initialization window.
end
if ARGV[1] == 'MATCH' then return 0 end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if now >= deadline or deadline - now > 31000 or now >= finish then return -3 end
if redis.call('TYPE', KEYS[1]).ok ~= 'string' or redis.call('GET', KEYS[1]) ~= ARGV[7] then return -5 end
-- The one dataset mutation is last. Future OPEN must wait until this fixed deadline has elapsed.
redis.call('HSET', KEYS[2], unpack(pairs))
return 1
