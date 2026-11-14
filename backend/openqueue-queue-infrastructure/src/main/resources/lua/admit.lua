-- QUE-001/004/005/006/007/008: 입장 후보 선정 (원자적). 계약: docs/database/redis-keys.md §3
-- KEYS[1] waiting, KEYS[2] waiting_seen, KEYS[3] admitted, KEYS[4] admissions, KEYS[5] admit_result(멱등 키)
-- ARGV[1] requested_slots, ARGV[2] now_ms, ARGV[3] waiting_ttl_ms, ARGV[4] lease_ttl_ms, ARGV[5] concurrency_limit,
-- ARGV[6] rate_max_admissions (0 = 무제한), ARGV[7] rate_window_ms, ARGV[8] terminal_retention_ms,
-- ARGV[9] result_retention_ms, ARGV[10] session_key_prefix
-- 반환: 선정된 session_id 를 ','로 이은 문자열 (없으면 빈 문자열). 재전송이면 최초 결과 문자열.
--
-- 처리 순서는 메모리 구현 WaitingQueue.admit 과 같다:
--   멱등 확인 → lease 회수 → 대기 만료 → 입장률 창 정리 → min(요청, 남은 슬롯, 입장률 허용) 선정 → 기록

local prev = redis.call('GET', KEYS[5])
if prev then
  -- 재전송: 상태를 바꾸지 않고 최초 결과를 돌려준다(D-QUE-001-1). 입장률도 소비하지 않는다(AC-QUE-006-7).
  return prev
end

local now = tonumber(ARGV[2])
local prefix = ARGV[10]

-- zkey 에서 score <= threshold 인 세션을 EXPIRED 로 전이한다. also 가 있으면 그 ZSET 에서도 제거한다.
local function expire_members(zkey, threshold, also)
  local stale = redis.call('ZRANGEBYSCORE', zkey, '-inf', threshold)
  for _, sid in ipairs(stale) do
    redis.call('ZREM', zkey, sid)
    if also then
      redis.call('ZREM', also, sid)
    end
    local skey = prefix .. sid
    redis.call('HSET', skey, 'status', 'EXPIRED')
    redis.call('PEXPIRE', skey, ARGV[8])
  end
end

expire_members(KEYS[3], now - tonumber(ARGV[4]), nil)      -- 입장 lease 회수 (AC-QUE-007-5)
expire_members(KEYS[2], now - tonumber(ARGV[3]), KEYS[1])  -- 대기 만료 (AC-QUE-004-6)

-- 세 상한의 최솟값: 요청 인원, 남은 슬롯(QUE-005), 입장률 허용(QUE-006)
local slots = tonumber(ARGV[1])
local free = tonumber(ARGV[5]) - redis.call('ZCARD', KEYS[3])
if free < slots then
  slots = free
end
local rate_max = tonumber(ARGV[6])
if rate_max > 0 then
  -- 창 밖(t + window <= now) 입장 사건 제거 후 남은 허용량 계산 (D-QUE-006-3)
  redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', now - tonumber(ARGV[7]))
  local allowance = rate_max - redis.call('ZCARD', KEYS[4])
  if allowance < slots then
    slots = allowance
  end
end
if slots < 0 then
  slots = 0
end

local selected = {}
if slots > 0 then
  selected = redis.call('ZRANGE', KEYS[1], 0, slots - 1)
end
for _, sid in ipairs(selected) do
  redis.call('ZREM', KEYS[1], sid)
  redis.call('ZREM', KEYS[2], sid)
  -- 입장 시각이 lease 시작 시각이다(D-QUE-007-1)
  redis.call('ZADD', KEYS[3], now, sid)
  redis.call('HSET', prefix .. sid, 'status', 'ADMITTED', 'last_seen_at', ARGV[2])
  if rate_max > 0 then
    -- 입장 사건 기록. 같은 ms 에 여러 건이 있어도 멤버가 겹치지 않도록 session_id 를 붙인다.
    redis.call('ZADD', KEYS[4], now, ARGV[2] .. ':' .. sid)
  end
end

local joined = table.concat(selected, ',')
-- 빈 결과도 기록해 재전송 응답을 고정한다(D-QUE-005-3)
redis.call('SET', KEYS[5], joined, 'PX', ARGV[9])
return joined
