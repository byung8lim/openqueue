-- QUE-001/003/004: 대기 세션 등록·재접속·재진입 (원자적). 계약: docs/database/redis-keys.md §3
-- KEYS[1] seq, KEYS[2] waiting, KEYS[3] waiting_seen, KEYS[4] session
-- ARGV[1] session_id, ARGV[2] now_ms, ARGV[3] reentry (ALLOW | DENY)
-- 반환: REGISTERED | ALREADY_WAITING | ALREADY_ADMITTED | REENTRY_DENIED

local function append()
  -- FIFO 순서는 단조 증가 시퀀스로 결정한다(같은 ms 등록의 순서 보장). 활동 시각은 별도 ZSET 에 둔다.
  local seq = redis.call('INCR', KEYS[1])
  redis.call('ZADD', KEYS[2], seq, ARGV[1])
  redis.call('ZADD', KEYS[3], ARGV[2], ARGV[1])
  redis.call('HSET', KEYS[4], 'status', 'WAITING', 'registered_at', ARGV[2], 'last_seen_at', ARGV[2])
  -- 종단 상태에서 재진입하는 경우 보존 TTL 이 걸려 있으므로 해제한다.
  redis.call('PERSIST', KEYS[4])
end

local status = redis.call('HGET', KEYS[4], 'status')
if not status then
  append()
  return 'REGISTERED'
end
if status == 'WAITING' then
  -- 재접속: 순번 유지, 활동 시각만 갱신 (AC-QUE-004-4)
  redis.call('ZADD', KEYS[3], ARGV[2], ARGV[1])
  redis.call('HSET', KEYS[4], 'last_seen_at', ARGV[2])
  return 'ALREADY_WAITING'
end
if status == 'ADMITTED' then
  return 'ALREADY_ADMITTED'
end
-- EXPIRED / CANCELLED: 재진입 정책 (AC-QUE-004-5)
if ARGV[3] == 'DENY' then
  return 'REENTRY_DENIED'
end
append()
return 'REGISTERED'
