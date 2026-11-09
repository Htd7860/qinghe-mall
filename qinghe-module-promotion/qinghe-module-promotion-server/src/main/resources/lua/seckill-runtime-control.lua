-- Fixed first-epoch OPEN/CLOSE controls. DB readiness/stopping is separately authoritative.
local function uint(v, low, high)
    if not v or #v > 16 or not string.match(v, '^%d+$') then return nil end
    if #v > 1 and string.sub(v, 1, 1) == '0' then return nil end
    local n = tonumber(v)
    if not n or n ~= math.floor(n) or n < low or n > high then return nil end
    return n
end
if #KEYS ~= 2 or #ARGV < 13 or (ARGV[1] ~= 'OPEN' and ARGV[1] ~= 'OPEN_MATCH' and ARGV[1] ~= 'CLOSE' and ARGV[1] ~= 'RETURN_POOL') then return -4 end
local returning = ARGV[1] == 'RETURN_POOL'
local deadline = uint(ARGV[3], 1, 4102444799999)
local finish = uint(ARGV[5], 1, 4102444799999)
local count = uint(ARGV[6], 1, 200)
local start = uint(ARGV[8], 946684800000, 4102444799999)
local single = uint(ARGV[9], 1, 9999)
local member = uint(ARGV[10], 1, 2147483647)
if not deadline or not finish or not count or not start or not single or not member
    or start >= finish or deadline > finish or single > member or #ARGV ~= 10 + 3 * count + (returning and 2 or 0) then return -4 end
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
local identity = {'schema','1','epoch','1','initToken',ARGV[2],'initDeadline',ARGV[3],'definitionDigest',ARGV[4]}
if kind == 'hash' then
    if redis.call('PTTL', KEYS[2]) ~= -1 then return -1 end
    for i = 1, #identity, 2 do
        if redis.call('HGET', KEYS[2], identity[i]) ~= identity[i + 1] then return -1 end
    end
end
if ARGV[1] == 'CLOSE' then
    if kind == 'none' then
        identity[#identity + 1] = 'phase'
        identity[#identity + 1] = 'CLOSED'
        redis.call('HSET', KEYS[2], unpack(identity)) -- Missing-key closure tombstone, never inventory creation.
        return 1
    end
    local phase = redis.call('HGET', KEYS[2], 'phase')
    if phase == 'CLOSED' then return 2 end
    if phase ~= 'PAUSED' and phase ~= 'OPEN' then return -1 end
    redis.call('HSET', KEYS[2], 'phase', 'CLOSED')
    return 1
end
if kind == 'none' then return 0 end -- OPEN never creates a missing runtime.
local phase = redis.call('HGET', KEYS[2], 'phase')
if returning then
    if phase ~= 'CLOSED' then return -1 end
elseif phase ~= 'PAUSED' and phase ~= 'OPEN' then return -1 end
local liveMembers = uint(redis.call('HGET', KEYS[2], 'memberCount'), 0, 10000)
local liveRequests = uint(redis.call('HGET', KEYS[2], 'requestCount'), 0, 30000)
if not liveMembers or not liveRequests or liveMembers > liveRequests or redis.call('HLEN', KEYS[2]) ~= #pairs / 2 + 2 * liveMembers + liveRequests then return -1 end
for i = 1, #pairs, 2 do
    local field = pairs[i]
    if field ~= 'phase' and field ~= 'memberCount' and field ~= 'requestCount'
        and not string.match(field, ':available$') and not string.match(field, ':held$')
        and not string.match(field, ':sold$') and not string.match(field, ':returned$') then
        if redis.call('HGET', KEYS[2], field) ~= pairs[i + 1] then return -1 end
    end
end
if not uint(redis.call('HGET', KEYS[2], 'memberCount'), 0, 9007199254740991)
    or not uint(redis.call('HGET', KEYS[2], 'requestCount'), 0, 9007199254740991) then return -1 end
for i = 1, count do
    local base = 10 + (i - 1) * 3
    local prefix = 'sku:' .. ARGV[base + 1] .. ':'
    local q = tonumber(ARGV[base + 2])
    local available = uint(redis.call('HGET', KEYS[2], prefix .. 'available'), 0, q)
    local held = uint(redis.call('HGET', KEYS[2], prefix .. 'held'), 0, q)
    local sold = uint(redis.call('HGET', KEYS[2], prefix .. 'sold'), 0, q)
    local returned = uint(redis.call('HGET', KEYS[2], prefix .. 'returned'), 0, q)
    if not available or not held or not sold or not returned or available + held + sold + returned ~= q then return -1 end
end
if returning then
    local sku = uint(ARGV[11 + 3 * count], 1, 9007199254740991)
    local target = uint(ARGV[12 + 3 * count], 1, 2147483647)
    if not sku or not target then return -4 end
    local quantity = nil
    for i = 1, count do
        local base = 10 + (i - 1) * 3
        if ARGV[base + 1] == ARGV[11 + 3 * count] then quantity = tonumber(ARGV[base + 2]) end
    end
    if not quantity or target > quantity then return -4 end
    local prefix = 'sku:' .. ARGV[11 + 3 * count] .. ':'
    local applied = uint(redis.call('HGET', KEYS[2], prefix .. 'returned'), 0, quantity)
    local available = uint(redis.call('HGET', KEYS[2], prefix .. 'available'), 0, quantity)
    if not applied or not available then return -1 end
    if target <= applied then return 2 end -- Older receipts never lower a newer cumulative return.
    local delta = target - applied
    if available < delta then return -1 end -- Wait for pending release/refund projection; never borrow held/sold.
    redis.call('HSET', KEYS[2], prefix .. 'available', tostring(available - delta), prefix .. 'returned', tostring(target))
    return 1
end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if now < deadline or now >= finish then return -3 end
if phase == 'OPEN' then return 2 end -- Preserve all live counters on duplicate OPEN.
if ARGV[1] == 'OPEN_MATCH' then return 0 end
for i = 1, #pairs, 2 do
    if redis.call('HGET', KEYS[2], pairs[i]) ~= pairs[i + 1] then return -1 end
end
redis.call('HSET', KEYS[2], 'phase', 'OPEN')
return 1
