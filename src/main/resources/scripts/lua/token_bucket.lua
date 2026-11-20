-- Token Bucket Distributed Rate Limiter
-- KEYS[1] = Rate limit key (e.g. smartproxy:rl:token_bucket:192.168.1.1)
-- ARGV[1] = Max burst capacity (tokens)
-- ARGV[2] = Replenish rate (tokens per second)
-- ARGV[3] = Current timestamp (seconds with fraction or unix epoch)
-- ARGV[4] = Requested tokens (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local replenish_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- Calculate fill time for full bucket in seconds to set appropriate TTL
local ttl = math.ceil(capacity / replenish_rate) * 2

local data = redis.call('HMGET', key, 'tokens', 'last_updated')
local tokens = tonumber(data[1])
local last_updated = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_updated = now
else
    local delta = math.max(0, now - last_updated)
    local tokens_to_add = delta * replenish_rate
    tokens = math.min(capacity, tokens + tokens_to_add)
    last_updated = now
end

local allowed = 0
local remaining = tokens

if tokens >= requested then
    allowed = 1
    tokens = tokens - requested
    remaining = tokens
end

redis.call('HMSET', key, 'tokens', tokens, 'last_updated', last_updated)
redis.call('EXPIRE', key, ttl)

-- Return: [allowed (1 or 0), remaining_tokens, reset_ttl_seconds]
return { allowed, math.floor(remaining), ttl }
