-- QUE-004/007: 활동 신호. WAITING 은 대기 TTL, ADMITTED 는 lease 를 연장한다.
-- KEYS[1] waiting_seen, KEYS[2] admitted, KEYS[3] session
-- ARGV[1] session_id, ARGV[2] now_ms
-- 반환: 1 (갱신됨) | 0 (종단 상태·미등록, 변경 없음)

local status = redis.call('HGET', KEYS[3], 'status')
if status == 'WAITING' then
  redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
elseif status == 'ADMITTED' then
  redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
else
  return 0
end
redis.call('HSET', KEYS[3], 'last_seen_at', ARGV[2])
return 1
