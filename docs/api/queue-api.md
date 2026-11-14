# Queue API 계약 (DMZ Queue Engine, Phase 3)

- 요구사항: QUE-010~013 (SRS §3). 오류·로그 규약: `error-and-logging-conventions.md`
- 기본 경로: `/queue/v1/sites/{siteId}/queues/{queueId}/session` — 식별자는 D-QUE-001-3 형식, 위반 시 400(원문 미반영)
- 세션 식별은 **쿠키만** 사용한다(D-API-001). 본문·헤더·쿼리의 세션 ID는 무시한다.
- 모든 응답은 `X-Request-Id`를 포함한다(요청에 없으면 서버가 생성, OBS-001 선반영).

## 1. 엔드포인트

| 메서드·경로 | 동작 | 성공 | 실패 |
|---|---|---|---|
| `POST …/session` | 대기 등록. 유효한 쿠키가 있으면 재진입(QUE-004), 없거나 미등록 ID면 새 세션 발급 | 200 + `Set-Cookie` + SessionResponse(재진입 포함, 항상 쿠키 재발급) | 400 QUE-INPUT-001, 404 QUE-QUEUE-001, 429, 503 |
| `GET …/session` | 상태 조회(상태 불변) | 200 SessionResponse | 400, 401 QUE-SESSION-001(쿠키 없음), 404 QUE-SESSION-002/QUE-QUEUE-001, 429, 503 |
| `POST …/session/heartbeat` | lastSeen 갱신·TTL 연장(QUE-007) | 200 SessionResponse | GET과 동일 |
| `DELETE …/session` | 대기 취소(종단 상태 CANCELLED) | 200 SessionResponse(취소 후 상태) | GET과 동일 |
| `GET …/session/events` | SSE 상태 스트림(QUE-013) | 200 `text/event-stream` | GET과 동일(스트림 열기 전) |

### SessionResponse (JSON, null 필드 생략)

| 필드 | 타입 | 설명 |
|---|---|---|
| siteId, queueId | string | 대기열 범위 |
| status | `WAITING` / `ADMITTED` / `EXPIRED` / `CANCELLED` | 도메인 `WaitingSessionStatus` |
| position | int? | WAITING 일 때 1부터 시작하는 순번, 그 외 생략 |
| waitingCount | int | 현재 대기 인원 |
| registeredAt, lastSeenAt, expiresAt | ISO-8601 | 등록·마지막 활동·만료(서버 시각 기준) |
| reentryDenied | boolean | 재진입 정책(QUE-004)에 의해 거부된 등록이면 true(종단 상태 유지) |
| serverTime | ISO-8601 | 클라이언트 시계 보정용 |

### SSE 이벤트

| event | data | 의미 |
|---|---|---|
| `status` | SessionResponse | 설정 주기(`openqueue.sse.interval`, 기본 2s)마다 전송. 전송마다 heartbeat 수행(D-API-002) |
| `gone` | `{}` | 세션이 더 이상 없음(보존 기간 만료 등). 클라이언트는 재등록 안내(WUI-002) |

연결은 `openqueue.sse.timeout`(기본 5m) 뒤 서버가 닫고 EventSource 가 자동 재접속한다. `id` 는 서버 밀리초 시각.

## 2. 오류 (RFC 9457 ProblemDetail)

`type` = `urn:openqueue:problem:<code 소문자>`, `title` 은 한국어 안내, `detail` 에 입력 원문·세션 ID 미포함(SEC-001).

| HTTP | code | 상황 |
|---|---|---|
| 400 | QUE-INPUT-001 | 경로 식별자 형식 위반 |
| 401 | QUE-SESSION-001 | 세션 쿠키 없음 |
| 404 | QUE-SESSION-002 | 쿠키의 세션이 이 대기열에 없음(만료 보존 기간 경과 포함) |
| 404 | QUE-QUEUE-001 | 운영 중이 아닌 대기열 |
| 429 | QUE-RATE-001 | 클라이언트 주소별 분당 요청 상한 초과(`Retry-After` 초 단위) |
| 503 | QUE-STORE-001 | 저장소(Redis) 불가 — Fail-closed(QUE-009) |
| 500 | QUE-INTERNAL-001 | 그 외. 스택·메시지 미노출 |

## 3. 결정 사항 (사후 승인 요청)

| ID | 결정 | 근거 |
|---|---|---|
| D-API-001 | 세션 식별은 쿠키 `oq_session`(HttpOnly, Secure, SameSite=Lax, Path=`/queue/`, Max-Age=세션 TTL)만 사용 | 세션 ID 소지 = 권한. 스크립트 접근 차단·CSRF 완화. Phase 4 Gateway 가 Secure 전제(TLS) 보장 |
| D-API-002 | SSE 스트림이 열려 있는 동안 서버가 주기적으로 heartbeat 를 대신 수행 | "탭이 열려 있음 = 활성 세션"(설계서 7.3). 클라이언트 heartbeat 호출 생략 가능, 폴링 fallback 시에는 `POST /heartbeat` 사용 |
| D-API-003 | 사용자 rate limit 은 `remoteAddr` 기준 분 단위 고정 창(기본 120/min), 프록시 헤더 불신 | 분산·정밀 제한과 신뢰 프록시 IP 추출은 Phase 4 Gateway 책임 |
| D-API-004 | 저장소 불가 시 API 는 항상 503(Fail-closed). Fail-open 분기는 Gateway 에서 사이트 정책으로 결정 | QUE-009 기본값. DMZ API 는 입장 토큰을 발급하지 않으므로 open 분기 불필요 |
| D-API-005 | 세션 ID 는 `SecureRandom` 128bit 이상 → base64url(패딩 없음). 클라이언트가 보낸 미등록 ID 는 재사용하지 않고 새로 발급 | 세션 고정(session fixation)·추측 방지(SEC-002) |
| D-API-006 | 대기열 목록은 Phase 3 에서 설정 파일(`openqueue.queues`)로 고정, 정책 배포(Phase 6)로 교체 예정 | Phase 3 범위 축소 |
