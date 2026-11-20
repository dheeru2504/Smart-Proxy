-- Leaky Bucket Rate Limiter
-- KEYS[1] = Rate limit key
-- ARGV[1] = Capacity (max bucket size)
-- ARGV[2] = Leak rate (units leaked per second)
-- ARGV[3] = Current timestamp (seconds with fraction)
-- ARGV[4] = Amount to add (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local leak_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local amount = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'water', 'last_leak')
local water = tonumber(data[1])
local last_leak = tonumber(data[2])

if water == nil then
    water = 0
    last_leak = now
else
    local delta = math.max(0, now - last_leak)
    local leaked = delta * leak_rate
    water = math.max(0, water - leaked)
    last_leak = now
end

local allowed = 0
local remaining = 0

if (water + amount) <= capacity then
    allowed = 1
    water = water + amount
    remaining = math.floor(capacity - water)
else
    allowed = 0
    remaining = 0
end

local ttl = math.ceil(capacity / leak_rate) * 2
redis.call('HMSET', key, 'water', water, 'last_leak', last_leak)
redis.call('EXPIRE', key, ttl)

return { allowed, remaining, ttl }
