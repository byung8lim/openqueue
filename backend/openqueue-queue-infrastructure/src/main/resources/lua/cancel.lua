-- QUE-003/005: 대기 포기·입장 포기. ADMITTED 취소는 슬롯을 즉시 회수한다(AC-QUE-005-4).
-- KEYS[1] waiting, KEYS[2] waiting_seen, KEYS[3] admitted, KEYS[4] session
-- ARGV[1] session_id, ARGV[2] terminal_retention_ms
-- 반환: 1 (CANCELLED 로 전이) | 0 (종단 상태·미등록, 멱등)

local status = redis.call('HGET', KEYS[4], 'status')
if status ~= 'WAITING' and status ~= 'ADMITTED' then
  return 0
end
redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZREM', KEYS[3], ARGV[1])
redis.call('HSET', KEYS[4], 'status', 'CANCELLED')
-- 종단 상태 세션은 재진입 판정·조회 응답을 위해 보존 기간 동안만 남긴다(D-QUE-004-4, D-QUE-008-4).
redis.call('PEXPIRE', KEYS[4], ARGV[2])
return 1
