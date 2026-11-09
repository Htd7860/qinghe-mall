-- One hash / one final HSET per transition: inventory, member counters and original request move together.
-- No TTL or expiry-based release. Every transition requires its separately checked durable DB decision.
local function uint(v, low, high)
    if type(v) ~= 'string' or #v > 16 or not string.match(v, '^%d+$') then return nil end
    if #v > 1 and string.sub(v, 1, 1) == '0' then return nil end
    local n = tonumber(v)
    if not n or n ~= math.floor(n) or n < low or n > high then return nil end
    return n
end
if #KEYS ~= 1 or (#ARGV ~= 18 and #ARGV ~= 19) then return -4 end
local mode = ARGV[1]
if mode ~= 'RESERVE' and mode ~= 'CONFIRM' and mode ~= 'RELEASE' and mode ~= 'CLOSE_ACCEPTED'
    and mode ~= 'PAY_ACCEPTED' and mode ~= 'CANCEL_ORDER' and mode ~= 'REFUND_PAID' then return -4 end
if (mode == 'REFUND_PAID' and #ARGV ~= 19) or (mode ~= 'REFUND_PAID' and #ARGV ~= 18) then return -4 end
local deadline = uint(ARGV[3], 1, 4102444799999)
local start = uint(ARGV[5], 946684800000, 4102444799999)
local finish = uint(ARGV[6], 1, 4102444799999)
local single = uint(ARGV[7], 1, 9999)
local limit = uint(ARGV[8], 1, 2147483647)
local maxMembers = uint(ARGV[9], 1, 10000)
local maxRequests = uint(ARGV[10], 1, 30000)
local member = uint(ARGV[12], 1, 9007199254740991)
local sku = uint(ARGV[13], 1, 9007199254740991)
local quantity = uint(ARGV[14], 1, 9999)
local price = uint(ARGV[15], 1, 2000000000)
local quoteExpiry = uint(ARGV[17], 1, 4102444799999)
if not deadline or not start or not finish or not single or not limit or not maxMembers or not maxRequests
    or not member or not sku or not quantity or not price or not quoteExpiry or start >= finish or deadline > finish
    or single > limit or quantity > single or quoteExpiry > finish then return -4 end
for _, i in ipairs({2,11,18}) do
    if #ARGV[i] ~= 36 or not string.match(ARGV[i], '^[0-9a-f%-]+$') then return -4 end
end
for _, i in ipairs({4,16}) do
    if #ARGV[i] ~= 64 or not string.match(ARGV[i], '^[0-9a-f]+$') then return -4 end
end
if ARGV[11] ~= ARGV[18] then return -4 end -- Server quote UUID is the original request/reservation identity.
local kind = redis.call('TYPE', KEYS[1]).ok
if kind == 'none' then return 0 end -- Never reconstruct missing inventory or a missing epoch here.
if kind ~= 'hash' or redis.call('PTTL', KEYS[1]) ~= -1 then return -2 end
local metadata = {'schema','1','epoch','1','initToken',ARGV[2],'initDeadline',ARGV[3],
    'definitionDigest',ARGV[4],'startAt',ARGV[5],'endAt',ARGV[6],'singleLimit',ARGV[7],'memberLimit',ARGV[8]}
for i = 1, #metadata, 2 do
    if redis.call('HGET', KEYS[1], metadata[i]) ~= metadata[i + 1] then return -1 end
end
local skuCount = uint(redis.call('HGET', KEYS[1], 'skuCount'), 1, 200)
local memberCount = uint(redis.call('HGET', KEYS[1], 'memberCount'), 0, 10000)
local requestCount = uint(redis.call('HGET', KEYS[1], 'requestCount'), 0, 30000)
if not skuCount or not memberCount or not requestCount or memberCount > requestCount
    or redis.call('HLEN', KEYS[1]) ~= 13 + 6 * skuCount + 2 * memberCount + requestCount then return -1 end
local phase = redis.call('HGET', KEYS[1], 'phase')
if phase ~= 'OPEN' and phase ~= 'CLOSED' and phase ~= 'PAUSED' then return -1 end
local skuPrefix = 'sku:' .. ARGV[13] .. ':'
if redis.call('HGET', KEYS[1], skuPrefix .. 'price') ~= ARGV[15] then return -1 end
local allocated = uint(redis.call('HGET', KEYS[1], skuPrefix .. 'allocated'), 1, 2147483647)
local available = uint(redis.call('HGET', KEYS[1], skuPrefix .. 'available'), 0, 2147483647)
local held = uint(redis.call('HGET', KEYS[1], skuPrefix .. 'held'), 0, 2147483647)
local sold = uint(redis.call('HGET', KEYS[1], skuPrefix .. 'sold'), 0, 2147483647)
local returned = uint(redis.call('HGET', KEYS[1], skuPrefix .. 'returned'), 0, 2147483647)
if not allocated or not available or not held or not sold or not returned
    or available + held + sold + returned ~= allocated then return -1 end
local memberPrefix = 'member:' .. ARGV[12] .. ':'
local memberHeldValue = redis.call('HGET', KEYS[1], memberPrefix .. 'held')
local memberPaidValue = redis.call('HGET', KEYS[1], memberPrefix .. 'paid')
local newMember = not memberHeldValue and not memberPaidValue
local memberHeld = newMember and 0 or uint(memberHeldValue, 0, 2147483647)
local memberPaid = newMember and 0 or uint(memberPaidValue, 0, 2147483647)
if memberHeld == nil or memberPaid == nil or memberHeld + memberPaid > limit then return -1 end
local recordField = 'request:' .. ARGV[11]
local encoded = redis.call('HGET', KEYS[1], recordField)
local original = {ARGV[12],ARGV[13],ARGV[14],ARGV[15],ARGV[16],ARGV[17],ARGV[18],'R'}
local record = nil
if encoded then
    if #encoded > 512 then return -1 end
    local ok, decoded = pcall(cjson.decode, encoded)
    if not ok or type(decoded) ~= 'table' or (#decoded ~= 8 and #decoded ~= 9) then return -1 end
    record = decoded
    for i = 1, 7 do if record[i] ~= original[i] then return -6 end end
    if record[8] ~= 'R' and record[8] ~= 'C' and record[8] ~= 'X' and record[8] ~= 'P' then return -1 end
    if #record == 9 and (record[8] ~= 'P' or not uint(record[9], 1, quantity)) then return -1 end
end
if mode == 'REFUND_PAID' then
    local target = uint(ARGV[19], 1, quantity)
    if not target then return -4 end
    if not record then return 0 end
    if record[8] ~= 'P' then return -8 end
    local restored = #record == 9 and uint(record[9], 1, quantity) or 0
    if target <= restored then return 2 end -- Old/repeated cumulative proof never adds stock twice.
    local delta = target - restored
    if newMember or memberPaid < quantity or sold < delta or available + delta > allocated then return -1 end
    record[9] = tostring(target) -- Same bounded request field; paid/member counters stay unchanged.
    redis.call('HSET', KEYS[1], recordField, cjson.encode(record), skuPrefix .. 'sold', tostring(sold - delta),
        skuPrefix .. 'available', tostring(available + delta))
    return 1
end
if mode == 'CONFIRM' then
    if not record then return 0 end
    if record[8] == 'X' then return -8 end
    if record[8] == 'C' or record[8] == 'P' then return 2 end
    if newMember or memberHeld < quantity or held < quantity then return -1 end
    record[8] = 'C'
    redis.call('HSET', KEYS[1], recordField, cjson.encode(record))
    return 1
end
if mode == 'PAY_ACCEPTED' then
    if not record then return 0 end
    if record[8] == 'P' then return 2 end
    if record[8] == 'X' then return -8 end
    if newMember or memberHeld < quantity or held < quantity or sold + quantity > allocated
        or memberPaid + quantity > limit then return -1 end
    record[8] = 'P'
    redis.call('HSET', KEYS[1], recordField, cjson.encode(record), skuPrefix .. 'held', tostring(held - quantity),
        skuPrefix .. 'sold', tostring(sold + quantity), memberPrefix .. 'held', tostring(memberHeld - quantity),
        memberPrefix .. 'paid', tostring(memberPaid + quantity))
    return 1
end
if mode == 'RELEASE' or mode == 'CLOSE_ACCEPTED' or mode == 'CANCEL_ORDER' then
    if record and record[8] == 'P' then return -8 end -- A paid reservation is never released by an unpaid close.
    if mode == 'RELEASE' and record and record[8] == 'C' then return -8 end -- Rejection cannot erase an accepted cache fact.
    if record and record[8] == 'X' then return 2 end
    if not record then
        if mode ~= 'RELEASE' then return 0 end -- A committed acceptance had a reservation. Missing data needs recovery, not invented balances.
        -- Use the absolute protocol ceiling for rejection tombstones: a delayed RESERVE may carry an older, higher configured cap.
        -- At 30000 no old valid RESERVE can insert a new record, since records are never removed from this epoch.
        if requestCount >= 30000 then return -5 end
        original[8] = 'X' -- Reject-first tombstone blocks a late RESERVE of this original request.
        redis.call('HSET', KEYS[1], recordField, cjson.encode(original), 'requestCount', tostring(requestCount + 1))
        return 1
    end
    if newMember or memberHeld < quantity or held < quantity or available + quantity > allocated then return -1 end
    record[8] = 'X'
    redis.call('HSET', KEYS[1], recordField, cjson.encode(record), skuPrefix .. 'available', tostring(available + quantity),
        skuPrefix .. 'held', tostring(held - quantity), memberPrefix .. 'held', tostring(memberHeld - quantity))
    return 1
end
if record then
    if record[8] == 'X' then return -7 end
    if record[8] == 'P' then return 2 end -- Late RESERVE observes the original paid request without touching held counters.
    if newMember or memberHeld < quantity or held < quantity then return -1 end
    return 2 -- Existing R/C remains the original reservation; no duplicate decrement or renewal.
end
if phase ~= 'OPEN' then return -3 end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if now < start or now < deadline or now >= finish or now >= quoteExpiry then return -3 end
if requestCount >= maxRequests or newMember and memberCount >= maxMembers then return -5 end
if available < quantity or memberHeld + memberPaid + quantity > limit then return -9 end
local changes = {recordField,cjson.encode(original),skuPrefix .. 'available',tostring(available - quantity),
    skuPrefix .. 'held',tostring(held + quantity),memberPrefix .. 'held',tostring(memberHeld + quantity),
    memberPrefix .. 'paid',tostring(memberPaid),'requestCount',tostring(requestCount + 1)}
if newMember then changes[#changes + 1] = 'memberCount'; changes[#changes + 1] = tostring(memberCount + 1) end
redis.call('HSET', KEYS[1], unpack(changes))
return 1
