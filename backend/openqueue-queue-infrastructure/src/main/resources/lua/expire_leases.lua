-- QUE-007: 입장 lease 가 지난 ADMITTED 세션을 EXPIRED 로 전이해 슬롯을 회수한다 (last_seen_at + lease_ttl <= now).
-- KEYS[1] admitted
-- ARGV[1] now_ms, ARGV[2] lease_ttl_ms, ARGV[3] terminal_retention_ms, ARGV[4] session_key_prefix
-- 반환: 만료된 session_id 배열(마지막 활동 시각 오름차순)

local threshold = tonumber(ARGV[1]) - tonumber(ARGV[2])
local stale = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', threshold)
for _, sid in ipairs(stale) do
  redis.call('ZREM', KEYS[1], sid)
  local skey = ARGV[4] .. sid
  redis.call('HSET', skey, 'status', 'EXPIRED')
  redis.call('PEXPIRE', skey, ARGV[3])
end
return stale
