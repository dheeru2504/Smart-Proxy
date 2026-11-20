-- Sliding Window Log Rate Limiter using Redis Sorted Set (ZSET)
-- KEYS[1] = Rate limit key (e.g. smartproxy:rl:sliding_window:192.168.1.1)
-- ARGV[1] = Max allowed requests in window
-- ARGV[2] = Window size in milliseconds (e.g. 60000 for 1 minute)
-- ARGV[3] = Current timestamp in milliseconds
-- ARGV[4] = Unique request ID (member for ZSET)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local request_id = ARGV[4]

local window_start = now - window_size

-- 1. Remove all records outside the current sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- 2. Count requests in the current sliding window
local current_count = redis.call('ZCARD', key)

local allowed = 0
local remaining = 0

if current_count < limit then
    allowed = 1
    redis.call('ZADD', key, now, request_id)
    remaining = limit - (current_count + 1)
else
    allowed = 0
    remaining = 0
end

-- Set TTL to ensure key expires if client stops sending requests
local ttl = math.ceil(window_size / 1000)
redis.call('EXPIRE', key, ttl)

return { allowed, remaining, ttl }
