# Queue Redis key 설계 및 Lua 스크립트 계약

- 문서 버전: 0.1 (TASK-005, Phase 2)
- 관련 요구사항: QUE-008(원자적 등록·입장), QUE-009(Fail-open/closed), NET-003(Redis 배치), SRS §3 QUE-001~007
- 구현: `backend/openqueue-queue-infrastructure` (`RedisKeySpace`, `RedisWaitingQueue`, `src/main/resources/lua/*.lua`)
- 변경 등급: Redis 계약 변경은 **I3** — 변경 시 이 문서·통합 테스트·호환 전환 계획을 함께 갱신하고 승인을 받는다.

## 1. 원칙

1. Redis 는 **대기열 런타임 상태 저장소**다. 영속 정책 DB 로 쓰지 않는다(정책은 서명 스냅샷으로 메모리에 적용, ADR-0009).
2. 상태를 바꾸는 연산은 **Lua 스크립트 한 번**으로 수행한다. 다중 Queue Engine 노드가 같은 대기열을 동시에 다뤄도 FIFO·멱등·상한 불변조건이 유지된다.
3. 규칙의 기준 구현은 도메인 메모리 모델 `WaitingQueue` 이며, Redis 구현은 같은 포트 `QueueOperations` 를 같은 의미로 구현한다. 차이는 시간 정밀도(ms)만 허용한다.
4. 모든 시간은 호출자가 넘기는 epoch millis 다(D-QUE-004-1). 스크립트는 `TIME` 을 호출하지 않는다(노드 간 동일 기준·결정적 테스트).

## 2. key 설계

접두어 `oq:{<site_id>:<queue_id>}:` — 중괄호가 **hash tag** 이므로 한 대기열의 모든 key 가 Cluster 에서 같은 슬롯에 놓인다.
식별자는 `[a-z0-9-]`(D-QUE-001-3)로 검증되어 구분자 `:`·`{}` 와 충돌하지 않는다.

| key | 자료구조 | member / field | score / value | TTL | 용도 |
|---|---|---|---|---|---|
| `…:seq` | STRING | - | 단조 증가 정수 (INCR) | 없음 | FIFO 순번. 같은 ms 등록의 순서 보장 |
| `…:waiting` | ZSET | session_id | 등록 시퀀스 | 없음 | 대기 순서. WAITING 만 포함 |
| `…:waiting_seen` | ZSET | session_id | last_seen_at ms | 없음 | 대기 TTL 만료 판정 (QUE-004) |
| `…:admitted` | ZSET | session_id | last_seen_at ms | 없음 | 입장 집합. lease 만료 판정·활성 입장 수 (QUE-005/007) |
| `…:admissions` | ZSET | `<ms>:<session_id>` | 입장 시각 ms | 없음(창 밖 정리) | 입장률 슬라이딩 창 (QUE-006). `Unlimited` 면 비어 있음 |
| `…:session:<session_id>` | HASH | `status`, `registered_at`, `last_seen_at` | 문자열 / ms | 종단 상태만 `terminal_session` 보존 기간 | 세션 조회·재진입 판정 |
| `…:admit:<request_id>` | STRING | - | 선정 session_id 를 `,` 로 이은 문자열(빈 문자열 가능) | `admission_result` 보존 기간 | 입장 요청 멱등 키 (QUE-001) |

보존 기간(`RedisRetentionPolicy`)은 기본값 없이 운영 정책으로 명시한다.

## 3. Lua 스크립트 계약

모든 스크립트는 `EVALSHA` 로 실행하며 `NOSCRIPT` 시 1회 `SCRIPT LOAD` 후 재시도한다(`RedisQueueScripts`). 오류는 Redis 오류 응답으로 전파되며 어댑터는
연결·타임아웃 오류만 `QueueUnavailableException` 으로 바꾼다. 스크립트 논리 오류(계약 위반)는 버그이므로 그대로 전파한다.

| 스크립트 | KEYS | ARGV | 반환 | 상태 전이 |
|---|---|---|---|---|
| `register.lua` | seq, waiting, waiting_seen, session | session_id, now_ms, reentry(ALLOW/DENY) | `REGISTERED` / `ALREADY_WAITING` / `ALREADY_ADMITTED` / `REENTRY_DENIED` | (없음)→WAITING, 종단→WAITING(ALLOW) |
| `heartbeat.lua` | waiting_seen, admitted, session | session_id, now_ms | 1 / 0 | 없음 (last_seen_at 갱신) |
| `cancel.lua` | waiting, waiting_seen, admitted, session | session_id, retention_ms | 1 / 0 | WAITING/ADMITTED→CANCELLED |
| `expire_waiting.lua` | waiting, waiting_seen | now_ms, waiting_ttl_ms, retention_ms, session_prefix | 만료 session_id 배열(대기 순서) | WAITING→EXPIRED |
| `expire_leases.lua` | admitted | now_ms, lease_ttl_ms, retention_ms, session_prefix | 만료 session_id 배열 | ADMITTED→EXPIRED |
| `admit.lua` | waiting, waiting_seen, admitted, admissions, admit_result | requested, now_ms, waiting_ttl_ms, lease_ttl_ms, limit, rate_max(0=무제한), rate_window_ms, retention_ms, result_retention_ms, session_prefix | 선정 session_id `,` 연결 문자열(재전송 시 최초 값) | WAITING→ADMITTED, 만료 회수 포함 |

`admit.lua` 처리 순서는 메모리 구현과 같다: 멱등 확인 → lease 회수 → 대기 만료 → 입장률 창 정리 → `min(요청, 남은 슬롯, 입장률 허용)` 선정 → 입장 사건·결과 기록.

경계 규칙(모두 동일 부등호): 만료/창 밖 = `t + ttl <= now`, 유효 = `t + ttl > now`. `availableSlots(now)` 는 `ZCOUNT admitted (now-lease_ttl, +inf)` 로 상태 변경 없이 계산한다.

## 4. 연결·보안 (NET-003)

- `RedisConnectionSettings(host, port, tls, credentials, commandTimeout)`: ACL 자격증명 **필수**, `tls=true` 면 서버 인증서 검증을 끌 수 없다.
- 자격증명은 Secret Manager/환경변수로 주입. `RedisCredentials.toString()` 은 비밀번호를 마스킹한다.
- ACL 권고(운영 적용은 Phase 7 설치 문서): Queue Engine 계정은 `~oq:*` key 패턴과 `+@read +@write +@scripting -@dangerous` 수준으로 제한한다.
  `FLUSHALL` 등은 금지한다(통합 테스트의 `flushall` 은 컨테이너 `default` 계정에서만 사용).
- 연결 실패·명령 타임아웃 → `QueueUnavailableException`. 어댑터는 자동 재시도하지 않고 호출자가 `FailurePolicy`(기본 FAIL_CLOSED)에 따라 처리한다.

## 5. 결정 사항 (TASK-005 중 기록, 승인 필요)

| ID | 결정 | 사유 |
|---|---|---|
| D-QUE-008-1 | 대기 순서는 `seq` 시퀀스 score, 활동 시각은 별도 ZSET(`waiting_seen`)으로 분리한다 | heartbeat 가 순번을 바꾸지 않아야 한다(QUE-001 불변조건 1). 같은 ms 등록도 순서가 결정적이다 |
| D-QUE-008-2 | 한 대기열의 모든 key 는 `oq:{site:queue}:` hash tag 를 공유하고, 세션 key 는 스크립트 안에서 prefix + id 로 구성한다 | Cluster 에서 다중 key Lua 가 가능하다. 세션 key 를 KEYS 로 다 넘기면 만료 대상 수가 가변이라 불가능 |
| D-QUE-008-3 | 입장률 창은 ZSET(입장 사건) + `ZREMRANGEBYSCORE` 로 **정확한 슬라이딩 창**을 구현한다 | 고정 창 카운터는 창 경계에서 2배 유입을 허용한다. 창 안 건수(`max_admissions`)만 유지해 메모리가 유한하다 |
| D-QUE-008-4 | 종단 상태 세션 해시와 멱등 결과에 보존 TTL 을 걸고, 보존 기간은 `RedisRetentionPolicy` 로 명시한다(기본값 없음) | 메모리 회수(D-QUE-004-4 의 Redis 측 보완). 재진입하면 `PERSIST` 로 TTL 을 푼다 |
| D-QUE-008-5 | 어댑터는 자동 재시도하지 않는다. 모든 변경 연산이 멱등이므로 재시도 여부·횟수는 호출자(Phase 3 API) 정책 | 어댑터 내부 재시도는 타임아웃을 곱으로 늘려 장애를 숨긴다. Fail-closed 판단은 상위 계층 책임(QUE-009) |
| D-QUE-008-6 | Phase 2 는 Standalone 엔드포인트 연결만 구현한다. Sentinel/Cluster 클라이언트 연계는 Phase 7(HA)에서 추가한다 | key 설계는 Cluster 호환이 끝났으므로 클라이언트 교체만 남는다. failover 시 초과 입장 방지(QUE-008 요구)는 HA 구성과 함께 검증해야 한다 |
| D-QUE-008-7 | 정책 값(TTL·상한·입장률)은 Redis 에 저장하지 않고 매 호출 ARGV 로 넘긴다 | 정책의 원천은 서명 스냅샷(메모리)이다. Redis 가 침해되어도 정책을 바꿀 수 없다 |

## 6. 검증

- 단위(로컬 실행): `RedisKeySpaceTest`, `RedisConnectionFactoryTest`(TLS/ACL URI 매핑, 연결 실패 → `QueueUnavailableException`), `LuaScriptTest`
- 통합(Docker, CI `redis-integration` 잡 또는 `mvn -Pintegration verify`): `RedisWaitingQueueIT` 15건 — AC-QUE-001~007 재검증 + AC-QUE-008-1~5(hash tag, TLS/ACL 설정, 보존 TTL, 다중 노드 공유 상태, 범위 격리)
- 미검증(Phase 7): Sentinel/Cluster failover, TLS 실연결, ACL 권한 축소 계정
