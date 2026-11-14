-- QUE-004: 대기 TTL 이 지난 WAITING 세션을 EXPIRED 로 전이한다 (last_seen_at + ttl <= now).
-- KEYS[1] waiting, KEYS[2] waiting_seen
-- ARGV[1] now_ms, ARGV[2] waiting_ttl_ms, ARGV[3] terminal_retention_ms, ARGV[4] session_key_prefix
-- 반환: 만료된 session_id 배열(대기 순서대로)
-- 세션 키는 prefix 와 session_id 로 구성한다. 모든 키가 같은 hash tag 를 공유하므로 Cluster 에서도 같은 슬롯이다(D-QUE-008-2).

local threshold = tonumber(ARGV[1]) - tonumber(ARGV[2])
local stale = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', threshold)

-- 활동 순서와 대기 순서가 다르므로 waiting 의 시퀀스로 다시 정렬해 반환한다(AC-QUE-004-1 "대기 순서대로").
local ordered = {}
for i, sid in ipairs(stale) do
  local score = redis.call('ZSCORE', KEYS[1], sid)
  ordered[i] = { sid = sid, seq = score and tonumber(score) or 0 }
end
table.sort(ordered, function(a, b) return a.seq < b.seq end)

local result = {}
for i, item in ipairs(ordered) do
  redis.call('ZREM', KEYS[1], item.sid)
  redis.call('ZREM', KEYS[2], item.sid)
  local skey = ARGV[4] .. item.sid
  redis.call('HSET', skey, 'status', 'EXPIRED')
  redis.call('PEXPIRE', skey, ARGV[3])
  result[i] = item.sid
end
return result
