# OpenQueue 요구사항 명세서 (SRS)

- 문서 버전: 0.1 (초안)
- 상위 문서: `docs/claude.md`(개발요청서), `docs/openqueue_architecture_security_design_ko.md`(아키텍처 설계서)
- 명세 형식: `docs/requirements/templates/requirement-template.md`
- 상태 정의: `초안` → `검토중` → `승인` → `변경요청` / `폐기`

> 코드가 존재한다는 사실만으로 요구사항이 구현된 것으로 간주하지 않는다.
> 검증 상태는 `docs/test/traceability-matrix.md`에서 관리한다.

---

## 1. 요구사항 ID 체계

| 접두어 | 영역 | 담당 모듈(설계서 9.1) |
|---|---|---|
| NET | 네트워크 및 Zone 보안 | M14, 인프라 |
| SEC | 애플리케이션 보안 | M11 |
| QUE | 대기열 도메인 | M02, M04 |
| GAT | Gateway 및 라우팅 | M01, M06 |
| TOK | 입장 토큰 | M05, M13 |
| POL | 정책 관리·배포 | M07 |
| ADM | 관리자 기능 | M08, M09 |
| WUI | Waiting Room UI | M03 |
| DB | 데이터 모델 및 저장 | M10 |
| OBS | 로그·모니터링 | M12 |
| OPS | 설치·운영 | M14 |
| NFR | 비기능 요구사항 | 전체 |

ID는 한 번 부여하면 재사용하지 않는다. 폐기된 요구사항은 `폐기` 상태로 남긴다.

---

## 2. 초기 요구사항 목록 (Phase 0 부여)

설계서의 원칙·기능 목록에서 도출한 초기 ID다. 상세 명세(7.2 형식)는 해당 Phase 착수 시 작성·승인한다.

### 2.1 네트워크 및 보안 경계 (NET)

| ID | 요구사항 | 근거 | Phase | 상태 |
|---|---|---|---|---|
| NET-001 | DMZ 구성요소는 MariaDB(DB Zone)에 네트워크·계정 수준에서 접근할 수 없어야 한다 | 설계서 SEC-01/05, 4.1 | 0, 6 | 초안 |
| NET-002 | Zone 간 통신은 기본 차단하며 port-matrix에 등록된 경로만 허용한다 | SEC-02 | 0 | 초안 |
| NET-003 | Queue Redis는 DMZ 전용 서브넷 사설 인터페이스에만 bind하고 Queue Engine만 접근한다 | 7.1, 7.2 | 2 | 초안 |
| NET-004 | DMZ Gateway는 등록된 Internal API Ingress 목적지로만 egress를 허용한다 | SEC-09, 4.1 | 4 | 초안 |
| NET-005 | Zone 간 허용 통신은 TLS, 서비스 간 통신은 mTLS를 적용한다 | SEC-08 | 4, 6 | 초안 |
| NET-006 | DMZ에는 내부 DB 계정·관리자 자격증명·장기 서명 개인키를 배치하지 않는다 | 4.2 | 0~8 | 초안 |

### 2.2 대기열 도메인 (QUE)

| ID | 요구사항 | 근거 | Phase | 상태 |
|---|---|---|---|---|
| QUE-001 | 동일 대기열에서 FIFO 순서로 입장 후보를 선정하고, 동일 입장 요청 재전송 시 중복 입장을 허용하지 않는다 | 요청서 7.3, 설계서 9.3 | 0(샘플), 1 | **검토중** (상세 §3) |
| QUE-002 | Site/Queue 식별자 모델 및 입력 검증 | DEC-12 | 1 | 검토중 (QUE-001 D-QUE-001-3로 구현, DEC-12 확정) |
| QUE-003 | 대기 세션 생성·조회·취소 | 9.3 | 1 | **검토중** (상세 §3, TASK-002) |
| QUE-004 | 세션 만료(TTL) 및 재접속 복구 규칙 | 9.3, 8.2 | 1 | **검토중** (상세 §3, TASK-002) |
| QUE-005 | 동시 입장 상한 기반 입장 가능 인원 계산 | 7.3 | 1 | **검토중** (상세 §3, TASK-003) |
| QUE-006 | 초당/분당 입장률 제한 | 9.3 | 1 | **검토중** (상세 §3, TASK-004) |
| QUE-007 | heartbeat 및 입장 lease 관리 | 7.3 | 1, 2 | **검토중** (상세 §3 도메인 부분, TASK-003; Redis 부분은 Phase 2) |
| QUE-008 | Redis Lua 기반 원자적 등록·입장 할당. Standalone/Sentinel/Cluster 공통 key 설계(hash tag), failover 초과 입장 방지 | 7.3, ADR-0004 | 2 | **검토중** (상세 §3, TASK-005; failover 검증은 Phase 7) |
| QUE-009 | 사이트별 Fail-open/Fail-closed 정책 적용. **기본값 Fail-closed**, 사이트별 변경 가능 | SEC-11, 12.2, DEC-09 | 2 | **검토중** (상세 §3, TASK-005; Gateway 분기는 Phase 4) |

### 2.3 그 외 영역 (상세화 예정)

| ID | 요구사항 | Phase | 상태 |
|---|---|---|---|
| SEC-001 | 로그·예외·HTTP 응답에 비밀정보와 토큰 원문을 노출하지 않는다 | 0~8 | 초안 |
| SEC-002 | 외부 입력(식별자, Host, path, header)은 모두 검증 후 사용한다 | 0~8 | 초안 |
| GAT-001 | Host/path 기반 사이트 식별 | 4 | 초안 |
| GAT-002 | 서비스 ID 기반 라우팅 및 임의 URL 프록시 금지 | 4 | 초안 |
| GAT-003 | 목적지 allowlist, DNS 해석 IP 검증, SSRF/DNS rebinding 방어 | 4 | 초안 |
| GAT-004 | 재기동 없는 런타임 라우트 원자 교체 (원천 DB → Distribution API 중개 → 서명 스냅샷, ADR-0007/0009) | 4 | 초안 (DEC-06 승인) |
| TOK-001 | 비대칭 서명 입장 토큰 claim 정의. 개인키는 내부 KMS 원칙, 임시 대안은 ADR-0005 §3 | 6 | 초안 (DEC-03) |
| TOK-004 | 기존 API Gateway/LB(내부 Ingress)용 토큰 검증 규격·공개키 배포 | 6 | 초안 (DEC-08) |
| TOK-002 | Internal Ingress에서 서명·iss·aud·exp·nbf·jti·site_id·queue_id·scope 검증 | 6 | 초안 |
| TOK-003 | 토큰 재사용(jti) 방지 | 6 | 초안 |
| POL-001 | 불변 버전 정책 스냅샷 생성 및 서명 | 6 | 초안 |
| POL-002 | DMZ 정책 검증 및 원자적 적용, 실패 시 마지막 정상 정책 유지 | 6 | 초안 |
| POL-003 | 노드별 적용 버전·오류 상태 관리 (Distribution API 상태 보고, 표시용) | 6 | 초안 (ADR-0009 §2.1) |
| POL-004 | DMZ ↔ 내부망 Distribution API: DMZ → 관리망 mTLS 단일 연결, 스냅샷 조회·SSE 알림·상태 보고, 공개키 설정 주입, 버전 단조 증가·구버전 거부, 로컬 서명 캐시, 캐시 없으면 Fail-closed, Admin API와 분리 | 6 | 초안 (DEC-02/06, ADR-0009) |
| ADM-001 | 관리자 인증(Keycloak/OIDC + MFA) 및 RBAC | 5 | 초안 (DEC-07, ADR-0006) |
| ADM-004 | 시스템 기본 Admin(내장 계정) id/password: 최초 부트업 시 `initpassword.txt` 생성(0600), 최초 로그인 시 변경 강제, 변경 후 파일 삭제 | 5 | 초안 (DEC-07 승인, ADR-0006) |
| ADM-002 | Site/Service/Queue/Routing Policy CRUD | 5 | 초안 |
| ADM-003 | 관리자 작업 감사 로그 | 5 | 초안 |
| WUI-001 | 대기 순번·예상 대기시간 표시 및 SSE 갱신 | 3 | 초안 |
| WUI-002 | 새로고침/재접속 시 세션 복구 및 만료 안내 | 3 | 초안 |
| DB-001 | MariaDB 스키마 Flyway 관리, SQL 파일 분리 | 5 | 초안 |
| OBS-001 | 요청 ID/상관관계 ID 부여 및 구조화 로그 | 4 | 초안 |
| OPS-001 | Zone별 분리 설치 패키지 | 7 | 초안 |
| OPS-002 | Linux 프로세스(systemd) / Docker / Kubernetes 3종 배포 지원 | 7 | 초안 (DEC-10) |
| NFR-001 | 동시 입장 상한이 부하 상황에서도 초과되지 않는다 | 7 | 초안 |

---

## 3. 상세 명세

### QUE-001 FIFO 입장 후보 선정 및 재전송 멱등성

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-001 |
| 기능명 | FIFO 입장 후보 선정 |
| 목적 | 먼저 대기열에 등록한 사용자가 먼저 입장하도록 보장하고, 입장 요청의 재전송·재시도로 인해 동일 세션이 중복 입장 처리되지 않도록 한다. |
| 설명 | 하나의 대기열(`site_id` + `queue_id` 범위) 안에서 대기 세션은 등록 순서대로 정렬된다. 입장 가능 인원 N이 주어지면 대기 순서 앞에서부터 최대 N개의 세션을 입장 후보로 선정한다. 입장 요청은 멱등 키(`admission_request_id`)를 가지며, 이미 처리된 키로 다시 요청하면 최초 결과를 그대로 반환하고 상태를 바꾸지 않는다. |
| 선행 조건 | 대기열 범위(site_id, queue_id)가 유효한 식별자로 생성되어 있다. |
| 입력 | (등록) `waiting_session_id` / (입장) `admission_request_id`, `available_slots`(0 이상 정수) |
| 정상 처리 흐름 | 1) 세션 등록 시 대기 순서 맨 뒤에 추가한다. 2) 입장 요청 시 대기 순서 앞에서부터 `min(available_slots, 대기 인원)`명을 선정해 `ADMITTED`로 전이하고 대기 순서에서 제거한다. 3) 선정 결과를 멱등 키에 기록한다. |
| 예외 처리 | `available_slots < 0` → 입력 오류(IllegalArgumentException). 식별자 형식 위반 → 입력 오류(메시지에 입력 원문을 포함하지 않음). null 입력 → 입력 오류. |
| 상태 변화 | (없음) → `WAITING` → `ADMITTED`. `ADMITTED`는 본 기능 범위에서 종단 상태다. (만료·취소는 QUE-003/004) |
| 권한 | 도메인 계층에는 권한 개념이 없다. 호출 주체 인증은 상위 계층(Phase 3 API) 책임이다. |
| 네트워크 Zone | DMZ (Queue Engine) |
| 데이터 저장 위치 | Phase 0~1: 메모리 도메인 모델. Phase 2: DMZ Queue Redis(원자성은 Lua Script가 보장). MariaDB에 저장하지 않는다. |
| 로그/감사 | 도메인 계층은 로그를 남기지 않는다. 세션 ID는 추후 로그 기록 시 마스킹 대상이다(SEC-001). |
| 성능 및 동시성 조건 | 도메인 객체는 스레드 안전하지 않다. 다중 노드/스레드 원자성은 QUE-008(Redis Lua)에서 보장한다. 등록·순번 조회·선정은 대기 인원에 대해 O(n) 이하. |
| 보안 고려사항 | 세션 ID는 추측 불가능한 opaque 값이어야 하며 형식(`[A-Za-z0-9_-]{16,128}`)을 검증한다. 오류 메시지에 외부 입력 원문을 반영하지 않는다(로그 인젝션 방지). |
| 연관 요구사항 | QUE-002, QUE-003, QUE-005, QUE-008, TOK-003 |

#### 수용 기준

```text
AC-QUE-001-1 (요청서 7.3 원문)
Given 동일한 queue_id에 대기 세션 A, B가 순서대로 등록되어 있고
When 입장 가능 인원이 1명 발생하면
Then 먼저 등록된 A만 입장 후보로 선정되어야 한다.
And B는 다음 순번(1번)으로 유지되어야 한다.
And 동일 요청이 재전송되어도 A가 중복 입장권을 발급받지 않아야 한다.

AC-QUE-001-2 중복 등록
Given 세션 A가 이미 대기 중일 때
When A가 다시 등록을 요청하면
Then 새로 추가되지 않고 기존 순번을 유지한다.

AC-QUE-001-3 입장 완료 세션 재등록
Given 세션 A가 이미 ADMITTED 상태일 때
When A가 다시 등록을 요청하면
Then 대기열에 다시 추가되지 않는다.

AC-QUE-001-4 경계값
Given 대기 인원이 2명일 때
When 입장 가능 인원이 0이면 아무도 선정되지 않고,
When 입장 가능 인원이 5이면 2명 모두 등록 순서대로 선정된다.
When 입장 가능 인원이 음수이면 입력 오류가 발생한다.

AC-QUE-001-5 다른 멱등 키
Given A가 요청 R1으로 입장했고 B가 대기 중일 때
When 새로운 요청 R2로 입장 가능 인원 1이 주어지면
Then B가 선정된다.

AC-QUE-001-6 식별자 검증
Given 형식에 맞지 않는 site_id/queue_id/session_id/request_id가 주어지면
Then 입력 오류가 발생하고 오류 메시지에 입력 원문이 포함되지 않는다.
```

#### 결정 사항 (TASK-000 중 기록, 승인 필요)

| ID | 결정 | 사유 |
|---|---|---|
| D-QUE-001-1 | 이미 처리된 멱등 키로 `available_slots`가 다른 요청이 와도 최초 결과를 반환한다 | 재전송은 동일 요청이라는 전제. 값이 다르면 호출자 오류이며 상태를 바꾸지 않는 것이 안전하다 |
| D-QUE-001-2 | 처리된 멱등 키 보관 기간은 도메인 모델에서 제한하지 않는다 | 실제 보관(TTL)은 Phase 2 Redis에서 정의한다(QUE-008) |
| D-QUE-001-3 | 식별자 형식: site_id/queue_id `^[a-z0-9][a-z0-9-]{0,63}$`, session_id `^[A-Za-z0-9_-]{16,128}$`, admission_request_id `^[A-Za-z0-9_-]{8,128}$` | Redis key namespace와 로그에 안전한 문자만 허용 |

#### 테스트

- 단위 테스트: `backend/openqueue-queue-domain/src/test/java/com/wbl/openqueue/queue/domain/WaitingQueueTest.java`, `IdentifierTest.java`
- 통합/보안 테스트: Phase 2 (Redis 원자성·동시성)에서 작성

---

### QUE-003 대기 세션 생성·조회·취소

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-003 |
| 기능명 | 대기 세션 생성·조회·취소 |
| 목적 | 사용자가 대기열에 들어오고(생성), 자신의 상태·순번을 확인하고(조회), 스스로 대기를 포기(취소)할 수 있게 한다. 취소된 세션이 입장 자원을 차지하지 않도록 한다. |
| 설명 | 세션 생성은 QUE-001의 등록과 같은 연산이며 등록 시각·마지막 활동 시각을 함께 기록한다. 조회는 상태(`WAITING`/`ADMITTED`/`EXPIRED`/`CANCELLED`), 순번, 등록 시각, 마지막 활동 시각을 돌려준다. 취소는 대기 중 세션을 대기 순서에서 제거하고 `CANCELLED`로 전이한다. 입장 완료(`ADMITTED`) 세션의 취소는 "입장 포기"이며 역시 `CANCELLED`로 전이한다(점유 자원 해제는 QUE-007 lease에서 다룸). |
| 선행 조건 | 대기열 범위와 대기열 정책(`QueuePolicy`, QUE-004)이 생성되어 있다. |
| 입력 | (생성) `waiting_session_id`, `now` / (조회) `waiting_session_id` / (취소) `waiting_session_id` |
| 정상 처리 흐름 | 1) 생성: 미등록 세션이면 `WAITING`으로 대기 순서 맨 뒤에 추가하고 `registered_at = last_seen_at = now`. 2) 조회: 세션 정보 반환, 대기 중이면 순번(1부터) 포함. 3) 취소: `WAITING` → 대기 순서에서 제거, 뒤 세션 순번 1씩 당겨짐, `CANCELLED`. `ADMITTED` → `CANCELLED`. |
| 예외 처리 | 미등록·이미 종단 상태(`EXPIRED`/`CANCELLED`) 세션의 취소는 오류가 아니라 "변경 없음(false)"으로 처리한다(멱등, 재전송 안전). 식별자·시각 null → 입력 오류. |
| 상태 변화 | `WAITING` → `CANCELLED`, `ADMITTED` → `CANCELLED`. `CANCELLED`는 종단 상태이며 재등록은 QUE-004 재진입 정책을 따른다. |
| 권한 | 도메인 계층에 권한 없음. 세션 소유 확인(세션 ID 소지 = 소유)은 API 계층(Phase 3) 책임. |
| 네트워크 Zone | DMZ (Queue Engine) |
| 데이터 저장 위치 | Phase 1 메모리. Phase 2 Redis. 종단 상태 세션의 보관 기간은 Redis TTL로 정의(QUE-008). |
| 로그/감사 | 도메인 계층 로그 없음. 취소·만료 건수는 지표 대상(OBS, Phase 7). |
| 성능 및 동시성 조건 | 스레드 안전하지 않음(QUE-001과 동일). 취소·조회는 대기 인원 n에 대해 O(n) 이하. |
| 보안 고려사항 | 타인의 세션 취소(IDOR)는 세션 ID의 추측 불가능성과 API 계층 소유 검증으로 막는다. 오류 메시지에 입력 원문 미포함. |
| 연관 요구사항 | QUE-001, QUE-004, QUE-007, WUI-*, SEC-001 |

#### 수용 기준

```text
AC-QUE-003-1 생성 후 조회
Given 세션 A를 시각 T0에 생성하면
Then 조회 결과는 status=WAITING, position=1, registered_at=T0, last_seen_at=T0 이다.

AC-QUE-003-2 취소 시 뒤 순번이 당겨진다
Given A, B, C가 순서대로 대기 중일 때
When B를 취소하면
Then B는 CANCELLED 이고 순번이 없으며, C의 순번은 2가 되고, 대기 인원은 2다.

AC-QUE-003-3 취소 멱등성
Given B가 이미 취소되었을 때
When B를 다시 취소하면
Then false 를 반환하고 상태는 CANCELLED 로 유지된다.

AC-QUE-003-4 미등록 세션 취소
When 등록된 적 없는 세션을 취소하면
Then false 를 반환하고 그 세션은 여전히 미등록(조회 결과 없음)이다.

AC-QUE-003-5 취소된 세션은 입장 후보가 되지 않는다
Given A, B가 대기 중이고 A가 취소되었을 때
When 입장 가능 인원 1이 주어지면
Then B가 선정된다.

AC-QUE-003-6 입장 완료 세션의 취소(입장 포기)
Given A가 ADMITTED 일 때
When A를 취소하면
Then true 를 반환하고 A는 CANCELLED 가 된다.
```

---

### QUE-004 세션 만료(TTL) 및 재접속 복구 규칙

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-004 |
| 기능명 | 대기 세션 만료 및 재접속 규칙 |
| 목적 | 브라우저를 닫거나 네트워크가 끊긴 사용자가 대기열에 남아 뒤 사용자의 입장을 막지 않게 하고, 새로고침·재접속한 사용자는 순번을 잃지 않게 한다. |
| 설명 | 대기열 정책 `QueuePolicy`에 대기 세션 TTL(`waiting_session_ttl`)과 재진입 정책(`reentry`: `ALLOW`/`DENY`)을 둔다. 세션은 활동 신호(heartbeat 또는 재등록)마다 `last_seen_at`을 갱신한다. `last_seen_at + ttl <= now` 인 `WAITING` 세션은 만료 처리(`EXPIRED`)되어 대기 순서에서 제거된다. 만료 처리는 명시적 연산 `expire(now)`로 수행하며, 입장 후보 선정(`admit`)은 선정 직전에 반드시 만료 처리를 먼저 수행한다(죽은 세션에 입장권을 주지 않기 위해). 종단 상태(`EXPIRED`/`CANCELLED`) 세션이 다시 등록하면 재진입 정책에 따라 맨 뒤에 새 세션으로 들어가거나(`ALLOW`) 거부된다(`DENY`). |
| 선행 조건 | `QueuePolicy`가 유효하다(ttl > 0). |
| 입력 | `QueuePolicy(waiting_session_ttl, reentry)`, (활동) `waiting_session_id`, `now`, (만료) `now` |
| 정상 처리 흐름 | 1) heartbeat: `WAITING`/`ADMITTED` 세션의 `last_seen_at = now`, true. 2) 재등록(재접속): `WAITING`이면 순번 유지 + `last_seen_at` 갱신, 결과 `ALREADY_WAITING`. 3) expire(now): 조건을 만족하는 `WAITING` 세션을 등록 순서대로 `EXPIRED` 처리하고 목록 반환. 4) 재진입: 종단 상태 세션 등록 시 `ALLOW` → 새 `WAITING`(맨 뒤, `registered_at = now`), `DENY` → `REENTRY_DENIED`. |
| 예외 처리 | ttl ≤ 0 또는 null, reentry null → 정책 생성 오류. 종단 상태·미등록 세션의 heartbeat → false(상태 불변). 시각 null → 입력 오류. |
| 상태 변화 | `WAITING` → `EXPIRED`. `EXPIRED`/`CANCELLED` → (재진입 ALLOW) 새 `WAITING`. 시간은 외부에서 `now`로 주입한다(도메인은 시계를 갖지 않는다). |
| 권한 | 없음(도메인). |
| 네트워크 Zone | DMZ (Queue Engine) |
| 데이터 저장 위치 | Phase 1 메모리. Phase 2에서 `last_seen_at`은 Redis ZSET score 또는 key TTL로 구현(QUE-008). |
| 로그/감사 | 없음(도메인). 만료 건수는 지표. |
| 성능 및 동시성 조건 | expire(now)는 대기 순서 앞에서부터 검사하되 등록 순서와 활동 순서가 다르므로 전체 O(n) 검사. Phase 2에서는 ZSET 범위 조회로 대체. |
| 보안 고려사항 | heartbeat는 세션 ID 소지자만 가능(API 계층). TTL을 지나치게 길게 두면 좀비 세션이 자원을 점유하므로 정책 상한은 Control Plane 검증(Phase 5)에서 둔다. |
| 연관 요구사항 | QUE-001, QUE-003, QUE-005, QUE-007, QUE-008, WUI-* |

#### 수용 기준

```text
AC-QUE-004-1 TTL 경과 시 만료
Given ttl=5분, A가 T0에 등록되고 B가 T0+1분에 등록되었을 때
When T0+5분에 expire 를 수행하면
Then A만 EXPIRED 가 되어 순번이 없고, B의 순번은 1이 된다.
And 반환 목록은 [A] 이다.

AC-QUE-004-2 경계값
Given ttl=5분, A가 T0에 등록되었을 때
When T0+5분-1ns 에 expire 하면 A는 WAITING 을 유지하고,
When T0+5분 에 expire 하면 A는 EXPIRED 가 된다 (last_seen_at + ttl <= now).

AC-QUE-004-3 heartbeat 로 만료 연장
Given ttl=5분, A가 T0에 등록되고 T0+4분에 heartbeat 했을 때
When T0+5분에 expire 하면 A는 WAITING 을 유지하고,
When T0+9분에 expire 하면 A는 EXPIRED 가 된다.

AC-QUE-004-4 재접속(재등록)은 순번을 유지하고 활동 시각을 갱신한다
Given ttl=5분, A, B가 순서대로 T0에 등록되었을 때
When A가 T0+4분에 다시 등록하면
Then 결과는 ALREADY_WAITING 이고 A의 순번은 1, last_seen_at=T0+4분이며
And T0+5분에 expire 하면 B만 만료된다.

AC-QUE-004-5 만료 세션 재진입
Given A가 EXPIRED 일 때
When reentry=ALLOW 이면 A의 재등록은 REGISTERED 이고 맨 뒤 순번·registered_at=now 를 받는다.
When reentry=DENY 이면 A의 재등록은 REENTRY_DENIED 이고 A는 EXPIRED 로 남는다.
And CANCELLED 세션도 같은 규칙을 따른다.

AC-QUE-004-6 입장 선정 전 만료 처리
Given ttl=5분, A가 T0에, B가 T0+1분에 등록되었을 때
When T0+5분에 입장 가능 인원 1로 admit 하면
Then B가 선정되고 A는 EXPIRED 다.

AC-QUE-004-7 종단 상태·미등록 세션의 heartbeat
Given A가 EXPIRED 또는 CANCELLED 이거나 미등록일 때
When heartbeat 하면 false 를 반환하고 상태가 바뀌지 않는다.

AC-QUE-004-8 정책 검증
When ttl 이 0, 음수, null 이거나 reentry 가 null 이면 정책 생성 시 입력 오류가 발생한다.
```

#### 결정 사항 (TASK-002 중 기록, 승인 필요)

| ID | 결정 | 사유 |
|---|---|---|
| D-QUE-004-1 | 도메인은 시계를 갖지 않고 모든 시간 의존 연산에 `now`(Instant)를 인자로 받는다 | 결정적 테스트(test-strategy §2-4), Redis Lua 구현 시 서버 시각 주입과 동일 구조 |
| D-QUE-004-2 | 만료는 명시적 `expire(now)` 연산에서만 일어나고, `admit`은 내부에서 먼저 `expire`를 호출한다. 조회 연산은 상태를 바꾸지 않는다 | 조회가 상태를 바꾸면 디버깅이 어렵다. 입장 선정만은 죽은 세션 배제가 정확성 요건이므로 예외 |
| D-QUE-004-3 | `QueuePolicy`에 기본값을 두지 않는다(생성 시 필수) | TTL·재진입 기본값은 운영 정책(Control Plane, Phase 5)에서 정한다. 도메인이 임의 숫자를 갖지 않게 한다 |
| D-QUE-004-4 | 종단 상태(`EXPIRED`/`CANCELLED`) 세션 정보는 메모리 모델에서 삭제하지 않는다 | 재진입 정책 판정과 조회 응답("만료되었습니다")에 필요. 보관 기간은 Phase 2 Redis TTL |
| D-QUE-004-5 | `ADMITTED` 세션의 만료는 QUE-007(lease)에서 다루며 QUE-004의 `expire`는 `WAITING`만 대상이다 | 입장 후 점유 시간은 대기 TTL과 다른 정책(lease TTL)이다 |

#### 테스트

- 단위 테스트: `backend/openqueue-queue-domain/src/test/java/com/wbl/openqueue/queue/domain/WaitingSessionLifecycleTest.java` (QUE-003/004), `WaitingQueueTest.java` (QUE-001 회귀, 시그니처 갱신)


---

### QUE-005 동시 입장 상한 기반 입장 가능 인원 계산

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-005 |
| 기능명 | 동시 입장 상한과 입장 가능 인원 계산 |
| 목적 | 원본 서비스가 동시에 수용할 수 있는 인원(동시 입장 상한)을 넘지 않도록, 현재 점유 중인 입장 수를 기준으로 이번에 입장시킬 수 있는 인원을 계산한다. 단순 FIFO 순번만으로는 상한을 보장할 수 없다(설계서 7.3). |
| 설명 | `QueuePolicy`에 동시 입장 상한 `concurrency_limit`(1 이상)을 둔다. "활성 입장"은 `ADMITTED` 상태이면서 입장 lease(QUE-007)가 아직 유효한 세션이다. `available_slots = concurrency_limit - 활성 입장 수`(0 미만이면 0). 입장 후보 선정(`admit`)은 이 값을 상한으로 하며, 호출자가 인원을 명시해도 남은 슬롯을 넘을 수 없다. |
| 선행 조건 | `QueuePolicy`가 유효하다(`concurrency_limit >= 1`, `admission_lease_ttl > 0`). |
| 입력 | (정책) `concurrency_limit` / (선정) `admission_request_id`, `now`, 선택적으로 `requested_slots`(0 이상) / (조회) `now` |
| 정상 처리 흐름 | 1) `admit(request_id, now)`: lease 만료 세션 회수(QUE-007) → 대기 만료 세션 정리(QUE-004) → `available_slots` 계산 → 대기 순서 앞에서부터 그 수만큼 `ADMITTED` 전이 → 멱등 키에 결과 기록. 2) `admit(request_id, requested_slots, now)`: 위와 같되 선정 인원은 `min(requested_slots, available_slots)`. 3) `availableSlots(now)`: 상태를 바꾸지 않고 현재 남은 슬롯을 돌려준다(lease 가 `now` 기준으로 유효한 `ADMITTED` 만 계산). |
| 예외 처리 | `concurrency_limit < 1` → 정책 생성 오류. `requested_slots < 0`, null 입력 → 입력 오류(상태 불변). 슬롯이 0이면 오류가 아니라 빈 선정 결과를 반환하고 멱등 키에 기록한다. |
| 상태 변화 | `WAITING` → `ADMITTED` (QUE-001과 동일). 상한 때문에 선정되지 않은 세션은 `WAITING` 과 순번을 유지한다. |
| 권한 | 없음(도메인). 정책 값 변경 권한은 Control Plane(Phase 5). |
| 네트워크 Zone | DMZ (Queue Engine) |
| 데이터 저장 위치 | Phase 1 메모리. Phase 2: 활성 입장 수는 Redis(lease key 집합 또는 카운터)에서 Lua 로 원자 계산(QUE-008). |
| 로그/감사 | 없음(도메인). 활성 입장 수·남은 슬롯은 지표(OBS). |
| 성능 및 동시성 조건 | 활성 입장 수 계산은 `ADMITTED` 세션 수에 대해 O(m). 다중 노드 원자성은 QUE-008. failover 시 카운터 과소 계산으로 상한을 넘는 문제는 QUE-008 복구 정책에서 다룬다. |
| 보안 고려사항 | 상한 초과 입장은 원본 서비스 과부하(가용성 침해)로 이어지므로 "활성 입장 수 ≤ 상한"을 도메인 불변조건으로 둔다. 호출자가 넘기는 인원 값도 신뢰하지 않고 상한으로 다시 제한한다. |
| 연관 요구사항 | QUE-001, QUE-003, QUE-006, QUE-007, QUE-008, OBS-* |

#### 수용 기준

```text
AC-QUE-005-1 상한만큼만 선정
Given concurrency_limit=2 이고 A, B, C 가 순서대로 대기 중일 때
When admit(now) 하면
Then A, B 가 선정되고 C 는 WAITING 으로 순번 1을 유지하며 availableSlots(now)=0 이다.

AC-QUE-005-2 슬롯이 없으면 빈 결과(오류 아님)
Given 슬롯이 0 인 상태에서 D 가 대기 중일 때
When 새 요청 R 로 admit(now) 하면
Then 선정 목록은 비어 있고 D 는 WAITING 이다.
And 같은 R 을 재전송해도 빈 결과가 그대로 반환된다.

AC-QUE-005-3 명시 인원도 남은 슬롯으로 제한
Given concurrency_limit=2 이고 A, B, C 가 대기 중일 때
When admit(request, requested_slots=5, now) 하면
Then A, B 만 선정된다.

AC-QUE-005-4 입장 포기(취소) 시 슬롯 회수
Given concurrency_limit=1 이고 A 가 ADMITTED, B 가 대기 중일 때
When A 를 취소하면 availableSlots(now)=1 이 되고,
When admit(now) 하면 B 가 선정된다.

AC-QUE-005-5 정책 검증
When concurrency_limit 이 0 또는 음수이면 정책 생성 시 입력 오류가 발생한다.

AC-QUE-005-6 조회는 상태를 바꾸지 않는다
Given concurrency_limit=1, lease_ttl=10분, A 가 T0 에 입장했을 때
When T0+10분에 availableSlots 를 조회하면 1 을 반환하지만
Then A 의 상태는 여전히 ADMITTED 다 (회수는 expireLeases/admit 에서만).
```

#### 결정 사항 (TASK-003 중 기록, 승인 필요)

| ID | 결정 | 사유 |
|---|---|---|
| D-QUE-005-1 | 호출자가 인원을 명시하는 `admit(request_id, requested_slots, now)` 도 남은 슬롯으로 상한 제한한다 | "활성 입장 수 ≤ 상한" 불변조건을 호출 경로와 무관하게 보장. QUE-006(입장률)은 이 인자로 더 작은 값을 넘기는 식으로 결합한다 |
| D-QUE-005-2 | `availableSlots(now)` 는 상태를 바꾸지 않고 `now` 기준 lease 유효 세션만 계산한다 | 조회 비변경 원칙(D-QUE-004-2) 유지. 회수(상태 전이)는 명시 연산에서만 |
| D-QUE-005-3 | 슬롯 0 인 `admit` 도 멱등 키에 빈 결과를 기록한다 | 재전송 시 동일 응답을 보장. 호출자는 새 멱등 키로 다음 주기에 재시도한다 |

#### 테스트

- 단위 테스트: `backend/openqueue-queue-domain/src/test/java/com/wbl/openqueue/queue/domain/AdmissionCapacityTest.java`

---

### QUE-006 초당/분당 입장률 제한

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-006 |
| 기능명 | 입장률(단위 시간당 입장 수) 제한 |
| 목적 | 동시 입장 상한(QUE-005)이 여유가 있어도 짧은 시간에 많은 사용자가 한꺼번에 입장해 원본 서비스에 순간 부하가 몰리는 것을 막는다. 운영자가 "분당 N명"처럼 입장 속도를 정할 수 있게 한다(설계서 9.3). |
| 설명 | `QueuePolicy`에 입장률 정책 `admission_rate` 를 둔다. 값은 `PerWindow(max_admissions, window)`(창 길이 `window` 동안 최대 `max_admissions` 건) 또는 `Unlimited`(제한 없음) 중 하나이며 반드시 명시한다(기본값 없음, D-QUE-004-3). 집계는 슬라이딩 창이다: 입장 시각 `t` 는 `t + window > now` 이면 창 안에 있다. 이번 선정에서 허용되는 인원은 `max_admissions - 창 안 입장 건수`(0 미만이면 0)이고, 최종 선정 인원은 `min(요청 인원, 남은 슬롯(QUE-005), 입장률 허용 인원)` 이다. 입장률은 "입장 사건" 기준이므로 입장 후 취소·lease 만료로 슬롯이 회수되어도 창 집계에는 남는다. 재전송(멱등 키 재사용)은 새 입장 사건이 아니므로 집계하지 않는다. |
| 선행 조건 | `QueuePolicy` 유효(`admission_rate` 가 null 이 아니고, `PerWindow` 면 `max_admissions >= 1`, `window > 0`). |
| 입력 | (정책) `admission_rate` / (선정) `admission_request_id`, `now`, 선택적으로 `requested_slots` |
| 정상 처리 흐름 | `admit` 처리 순서(QUE-005)에 다음을 끼워 넣는다: lease 회수 → 대기 만료 → **창 밖 입장 기록 제거** → `slots = min(요청, 남은 슬롯, 입장률 허용)` → 선정 → **선정 건마다 입장 시각 기록**(`PerWindow` 일 때만) → 멱등 키 기록. |
| 예외 처리 | `admission_rate` null → 정책 생성 오류. `max_admissions < 1`, `window` null·0·음수 → 정책 생성 오류. 입장률 때문에 선정이 0 이어도 오류가 아니라 빈 결과이며 멱등 키에 기록한다(D-QUE-005-3 과 동일). |
| 상태 변화 | 세션 상태 전이는 QUE-001/005 와 같다. 입장률 집계는 세션 상태가 아닌 대기열 내부 기록(입장 시각 목록)이다. |
| 권한 | 없음(도메인). 정책 값은 Control Plane(Phase 5). |
| 네트워크 Zone | DMZ (Queue Engine) |
| 데이터 저장 위치 | Phase 1 메모리(입장 시각 목록). Phase 2: Redis ZSET(score=입장 시각) 또는 고정 창 카운터로 Lua 내부에서 원자 집계(QUE-008). |
| 로그/감사 | 없음(도메인). 입장률 제한으로 미선정된 횟수는 지표(OBS). |
| 성능 및 동시성 조건 | 입장 기록은 창 안 건수만 유지하므로 메모리는 `max_admissions` 수준으로 유지된다. 창 밖 제거는 O(창 안 건수). `Unlimited` 는 기록하지 않는다. 다중 노드 집계는 QUE-008. |
| 보안 고려사항 | 입장률은 원본 서비스 보호(가용성) 장치다. 호출 측이 `now` 를 과거로 넘겨 창을 우회하는 것은 도메인에서 막지 않으며(시간은 신뢰 경계 안의 서버 시각), 시각은 Queue Engine 서버 시계만 사용한다는 전제를 Phase 3 API 에 기록한다. |
| 연관 요구사항 | QUE-001, QUE-005, QUE-007, QUE-008, OBS-* |

#### 수용 기준

```text
AC-QUE-006-1 창 안에서는 최대 건수까지만 입장
Given admission_rate=PerWindow(2, 1분), concurrency_limit=10 이고 A~E 가 대기 중일 때
When admit(R1, T0) 하면 A, B 가 선정되고
When admit(R2, T0+30초) 하면 아무도 선정되지 않는다(창 안 입장 2건).

AC-QUE-006-2 창 경계
Given 위 상태에서
When admit(R3, T0+1분-1ns) 하면 아무도 선정되지 않고,
When admit(R4, T0+1분) 하면 2명이 선정된다 (t + window <= now 이면 창 밖).

AC-QUE-006-3 슬라이딩 창
Given admission_rate=PerWindow(2, 1분) 이고 A~D 가 대기 중일 때
When admit(R1, 1, T0) → [A], admit(R2, 1, T0+30초) → [B] 이면
Then admit(R3, T0+1분) → [C] (B 가 아직 창 안이라 1명만),
And admit(R4, T0+1분30초) → [D] (B 가 창 밖으로 나가 1명 허용).

AC-QUE-006-4 동시 입장 상한과 결합(최솟값)
Given concurrency_limit=1, admission_rate=PerWindow(5, 1분), 3명 대기 → admit 하면 1명.
Given concurrency_limit=5, admission_rate=PerWindow(1, 1분), 3명 대기 → admit 하면 1명.

AC-QUE-006-5 명시 인원과 결합
Given admission_rate=PerWindow(2, 1분), concurrency_limit=10, 5명 대기
When admit(R, requested_slots=5, T0) 하면 2명이 선정된다.

AC-QUE-006-6 무제한
Given admission_rate=Unlimited, concurrency_limit=10, 5명 대기
When 같은 시각 T0 에 admit(R1, 3, T0), admit(R2, T0) 를 연속 수행하면 3명, 2명이 선정된다.

AC-QUE-006-7 재전송은 입장률을 소비하지 않는다
Given admission_rate=PerWindow(2, 1분), A~D 대기
When admit(R1, T0) → [A, B] 이고 같은 R1 을 재전송하면 같은 결과이며
Then admit(R2, T0+1분) 하면 C, D 가 선정된다(재전송이 집계되었다면 0명이었을 것).

AC-QUE-006-8 취소·lease 만료는 입장률 집계를 되돌리지 않는다
Given admission_rate=PerWindow(1, 1분), concurrency_limit=5, A, B 대기, A 가 T0 에 입장
When T0+10초에 A 를 취소하면 availableSlots=5 이지만
Then admit(R2, T0+10초) 는 아무도 선정하지 않는다(입장 사건 1건이 창 안).
And admit(R3, T0+1분) 은 B 를 선정한다.

AC-QUE-006-9 정책 검증
When admission_rate 가 null 이면 정책 생성 오류.
When PerWindow 의 max_admissions 가 0·음수이거나 window 가 null·0·음수이면 생성 오류.
```

#### 결정 사항 (TASK-004 중 기록, 승인 필요)

| ID | 결정 | 사유 |
|---|---|---|
| D-QUE-006-1 | 입장률은 `PerWindow(max, window)` 또는 `Unlimited` 를 **명시**하며 기본값을 두지 않는다 | D-QUE-004-3 과 일관. "제한 없음"도 운영자의 의도적 선택이어야 한다 |
| D-QUE-006-2 | 입장률은 "입장 사건" 기준 슬라이딩 창으로 집계하고, 취소·lease 만료로 슬롯이 회수되어도 집계를 되돌리지 않는다 | 입장률의 목적은 원본 서비스에 도달하는 **신규 유입 속도** 제어다. 취소된 사용자도 이미 유입 부하를 일으켰다. 점유(동시 접속) 보호는 QUE-005 가 담당하므로 두 장치의 기준을 분리한다 |
| D-QUE-006-3 | 창 포함 판정은 `t + window > now`(창 안), `t + window <= now`(창 밖)로 TTL 규칙(QUE-004/007)과 같은 부등호를 쓴다 | 세 가지 시간 규칙의 경계가 같아야 사람이 디버깅할 때 혼동이 없다 |
| D-QUE-006-4 | `Unlimited` 일 때는 입장 시각을 기록하지 않는다 | 창이 없으면 기록을 제거할 기준이 없어 메모리가 무한히 늘어난다 |
| D-QUE-006-5 | 입장률 정책 타입은 sealed interface `AdmissionRateLimit` + 두 record(`Unlimited`, `PerWindow`)로 표현한다 | "무제한"을 `Integer.MAX_VALUE` 같은 특수값이나 null 로 표현하면 읽는 사람이 의도를 알 수 없다. switch 로 모든 경우를 컴파일러가 강제한다 |

#### 테스트

- 단위 테스트: `backend/openqueue-queue-domain/src/test/java/com/wbl/openqueue/queue/domain/AdmissionRateLimitTest.java` (QUE-006), 기존 테스트는 정책 생성자 인자 추가만

---

### QUE-007 heartbeat 및 입장 lease 관리

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-007 |
| 기능명 | 입장 lease 와 heartbeat 기반 슬롯 회수 |
| 목적 | 입장한 사용자가 브라우저를 닫거나 네트워크가 끊겨도 동시 입장 슬롯을 영구 점유하지 않도록, 입장 권한을 TTL 이 있는 lease 로 관리하고 활동 신호(heartbeat)로 연장한다(설계서 7.3). |
| 설명 | `QueuePolicy`에 `admission_lease_ttl`(양수)을 둔다. 세션이 `ADMITTED` 로 전이되는 순간 `last_seen_at` 을 입장 시각으로 갱신하며, lease 만료 시각은 `last_seen_at + admission_lease_ttl` 이다(별도 필드 없음). heartbeat 는 `WAITING`/`ADMITTED` 모두 `last_seen_at` 을 갱신하므로 입장 후 heartbeat 가 lease 를 연장한다. `expireLeases(now)` 는 `last_seen_at + lease_ttl <= now` 인 `ADMITTED` 세션을 `EXPIRED` 로 전이해 슬롯을 회수한다. 입장 후보 선정(`admit`)은 선정 직전에 `expireLeases` 를 먼저 수행한다. QUE-004 의 `expire(now)` 는 `WAITING` 만 다루는 경계(D-QUE-004-5)를 유지한다. |
| 선행 조건 | `QueuePolicy` 유효(`admission_lease_ttl > 0`). |
| 입력 | (정책) `admission_lease_ttl` / (연장) `waiting_session_id`, `now` / (회수) `now` |
| 정상 처리 흐름 | 1) 입장: `ADMITTED`, `last_seen_at = now`. 2) heartbeat: `last_seen_at = now`(lease 연장), true. 3) `expireLeases(now)`: 조건 충족 `ADMITTED` 세션을 입장 순서대로 `EXPIRED` 처리하고 목록 반환. 4) 취소(QUE-003): `ADMITTED` → `CANCELLED`, 슬롯 즉시 회수. |
| 예외 처리 | `admission_lease_ttl` ≤ 0 또는 null → 정책 생성 오류. 시각 null → 입력 오류. 종단 상태 세션의 heartbeat → false(QUE-004 와 동일). |
| 상태 변화 | `ADMITTED` → `EXPIRED` (lease 만료). `EXPIRED` 는 종단 상태이며 재등록은 재진입 정책(QUE-004)을 따른다. |
| 권한 | 없음(도메인). heartbeat 호출 주체 검증은 API 계층(Phase 3). |
| 네트워크 Zone | DMZ (Queue Engine) |
| 데이터 저장 위치 | Phase 1 메모리. Phase 2: lease 는 Redis key TTL 또는 ZSET score(QUE-008). |
| 로그/감사 | 없음(도메인). lease 만료 건수는 지표. |
| 성능 및 동시성 조건 | `expireLeases` 는 `ADMITTED` 세션 전체 O(m) 검사(heartbeat 순서와 입장 순서가 다름). Phase 2 에서는 ZSET 범위 조회. |
| 보안 고려사항 | lease TTL 이 길면 이탈 사용자가 슬롯을 점유해 가용성이 떨어지고, 짧으면 정상 사용자가 끊긴다. 상·하한은 Control Plane 정책 검증(Phase 5). 입장 토큰(TOK)의 만료는 lease 와 별개 계약이며 Ingress 가 독립 검증한다(Phase 6). |
| 연관 요구사항 | QUE-003, QUE-004, QUE-005, QUE-008, TOK-*, WUI-* |

#### 수용 기준

```text
AC-QUE-007-1 lease 만료 시 EXPIRED 전이와 슬롯 회수
Given concurrency_limit=1, lease_ttl=10분, A 가 T0 에 입장했을 때
When T0+10분에 expireLeases 하면
Then 반환 목록은 [A], A 는 EXPIRED, availableSlots=1 이다.

AC-QUE-007-2 경계값
Given A 가 T0 에 입장했을 때
When T0+10분-1ns 에 expireLeases 하면 A 는 ADMITTED 를 유지하고,
When T0+10분 에 expireLeases 하면 A 는 EXPIRED 가 된다 (last_seen_at + lease_ttl <= now).

AC-QUE-007-3 heartbeat 로 lease 연장
Given A 가 T0 에 입장하고 T0+8분에 heartbeat 했을 때
When T0+10분에 expireLeases 하면 비어 있고,
When T0+18분에 expireLeases 하면 [A] 다.

AC-QUE-007-4 lease 는 입장 시각부터 시작한다
Given A 가 T0 에 등록되어 대기하다가 T0+4분에 입장했을 때
When T0+10분에 expireLeases 하면 A 는 ADMITTED 를 유지하고 last_seen_at=T0+4분이며,
When T0+14분에 expireLeases 하면 A 는 EXPIRED 가 된다.

AC-QUE-007-5 입장 선정 전 lease 회수
Given concurrency_limit=1, A 가 T0 에 입장하고 B 가 대기 중일 때
When T0+10분에 새 요청으로 admit(now) 하면
Then B 가 선정되고 A 는 EXPIRED 다.

AC-QUE-007-6 lease 만료 세션의 재진입
Given A 의 lease 가 만료되어 EXPIRED 일 때
When reentry=ALLOW 이면 재등록은 REGISTERED(맨 뒤) 이고,
When reentry=DENY 이면 REENTRY_DENIED 이며 EXPIRED 로 남는다.

AC-QUE-007-7 정책 검증
When admission_lease_ttl 이 0, 음수, null 이면 정책 생성 시 입력 오류가 발생한다.

AC-QUE-007-8 대기 만료(expire)는 ADMITTED 를 건드리지 않는다
Given waiting_ttl=5분, lease_ttl=10분, A 가 T0 에 입장했을 때
When T0+5분에 expire 하면 비어 있고 A 는 ADMITTED 다.
```

#### 결정 사항 (TASK-003 중 기록, 승인 필요)

| ID | 결정 | 사유 |
|---|---|---|
| D-QUE-007-1 | lease 만료 시각은 별도 필드 없이 `last_seen_at + admission_lease_ttl` 로 계산하고, 입장 시 `last_seen_at` 을 입장 시각으로 갱신한다 | 대기 TTL(QUE-004)과 같은 구조라 모델이 단순해지고 Redis 구현(ZSET score 하나)과도 일치한다. Waiting Room 이 만료 시각을 보여줘야 하면 정책 값으로 계산 가능 |
| D-QUE-007-2 | lease 만료 상태는 별도 상태를 두지 않고 `EXPIRED` 를 재사용한다 | 상태 수를 늘리지 않는다. "대기 만료"와 "입장 만료"를 사용자 안내에서 구분해야 하면 Phase 3 에서 `LEASE_EXPIRED` 분리 검토(소유자 결정 요청) |
| D-QUE-007-3 | `expire(now)`(WAITING, QUE-004)와 `expireLeases(now)`(ADMITTED, QUE-007)를 별도 연산으로 두고 `admit` 은 둘을 모두 선행 호출한다 | D-QUE-004-5 경계를 유지하고, 지표(대기 만료 vs lease 만료)를 분리 집계할 수 있다 |
| D-QUE-007-4 | lease 는 Queue Engine 내부의 슬롯 점유 개념이며 입장 토큰(TOK)의 만료·검증과 독립이다 | 토큰 계약은 Phase 6 승인 사항. 도메인이 토큰 수명을 가정하지 않는다 |

#### 테스트

- 단위 테스트: `backend/openqueue-queue-domain/src/test/java/com/wbl/openqueue/queue/domain/AdmissionCapacityTest.java` (QUE-005/007), 기존 `WaitingQueueTest`·`WaitingSessionLifecycleTest` (정책 생성자 인자 추가, 검증 내용 불변)

---

### QUE-008 Redis Lua 기반 원자적 등록·입장 할당

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-008 |
| 기능명 | Redis 어댑터(원자적 대기열 연산, Cluster 호환 key) |
| 목적 | 여러 Queue Engine 노드·스레드가 같은 대기열을 동시에 다뤄도 FIFO·멱등·동시 입장 상한·입장률 불변조건이 깨지지 않게 한다. |
| 설명 | 도메인 포트 `QueueOperations` 를 Redis 로 구현한다(`RedisWaitingQueue`). 상태 변경 연산(등록·heartbeat·취소·만료·lease 회수·입장 선정)은 각각 Lua 스크립트 1회로 수행한다. key·자료구조·스크립트 계약은 `docs/database/redis-keys.md` 가 원천이다. 메모리 구현과 의미가 같아야 하며 차이는 시간 정밀도(ms)만 허용한다. |
| 선행 조건 | Redis 7.x, ACL 계정, 네트워크 경로(NET-003). 정책은 메모리(서명 스냅샷)에서 ARGV 로 전달한다. |
| 입력 | `QueueOperations` 와 동일 + `RedisRetentionPolicy`(종단 세션·멱등 결과 보존 기간) |
| 정상 처리 흐름 | redis-keys.md §3 |
| 예외 처리 | 연결 실패·명령 타임아웃 → `QueueUnavailableException`(QUE-009). 스크립트 계약 위반은 Redis 오류로 전파(버그). 입력 검증은 도메인과 동일. |
| 상태 변화 | 도메인과 동일. 종단 상태 세션은 보존 기간 후 Redis 에서 사라진다(조회 시 "미등록"). |
| 권한 | Redis ACL: Queue Engine 전용 계정, `oq:*` key, read/write/scripting 만. |
| 네트워크 Zone | DMZ (Queue Engine → DMZ Queue Redis, 사설 인터페이스) |
| 데이터 저장 위치 | DMZ Queue Redis. MariaDB 미사용. 어댑터 모듈은 DB 드라이버 의존을 enforcer 로 금지한다. |
| 로그/감사 | 어댑터는 로그를 남기지 않는다(Phase 3 API 계층). 예외 메시지에 호스트·자격증명 미포함. |
| 성능 및 동시성 조건 | 스크립트 1회 = 1 RTT. 만료 회수는 만료 대상 수에 비례. hash tag 로 Cluster 에서 단일 슬롯 원자성. failover 초과 입장 방지는 Phase 7 HA 검증(D-QUE-008-6). |
| 보안 고려사항 | 정책 값을 Redis 에 두지 않아 Redis 침해로 정책 변조 불가(D-QUE-008-7). TLS 시 인증서 검증 강제. 평문 연결은 CI 컨테이너 전용. |
| 연관 요구사항 | QUE-001~007, QUE-009, NET-003, NET-006, OPS-* |

#### 수용 기준

```text
AC-QUE-008-1 한 대기열의 모든 key 는 같은 hash tag 접두어 oq:{site:queue}: 를 공유한다.
AC-QUE-008-2 TLS 설정을 켜면 SSL 과 서버 인증서 검증이 함께 켜지고 ACL 자격증명이 URI 에 전달된다.
AC-QUE-008-3 취소·만료된 세션 해시와 멱등 결과 key 에는 보존 TTL 이 걸리고, 재진입하면 TTL 이 풀린다.
AC-QUE-008-4 서로 다른 어댑터 인스턴스(노드)가 같은 대기열의 등록·순번·입장·멱등 결과를 공유한다.
AC-QUE-008-5 다른 대기열 범위의 연산은 서로 영향을 주지 않는다.
AC-QUE-008-6 AC-QUE-001~007 의 수용 기준이 Redis 구현에서도 성립한다(RedisWaitingQueueIT 가 대표 AC 를 재검증).
```

#### 결정 사항

D-QUE-008-1~7: `docs/database/redis-keys.md` §5 (승인 필요, Redis 계약 I3).

#### 테스트

- 단위(로컬): `RedisKeySpaceTest`, `RedisConnectionFactoryTest`, `LuaScriptTest`
- 통합(Docker/CI): `RedisWaitingQueueIT` — CI `redis-integration` 잡

---

### QUE-009 사이트별 Fail-open / Fail-closed 정책

| 항목 | 내용 |
|---|---|
| 요구사항 ID | QUE-009 |
| 기능명 | 대기열 저장소 장애 시 처리 정책 |
| 목적 | Redis 장애 시 원본 서비스 보호(차단)와 가용성(통과) 중 사이트가 선택한 쪽으로 일관되게 동작하게 한다. 기본값은 Fail-closed(DEC-09). |
| 설명 | 도메인에 `FailurePolicy { FAIL_CLOSED, FAIL_OPEN }`, `DEFAULT = FAIL_CLOSED` 를 둔다. 어댑터는 연결 실패·타임아웃을 `QueueUnavailableException` 으로 보고하고 자동 재시도하지 않는다. 실제 분기(차단 응답 vs 대기열 우회 통과)는 Gateway(Phase 4)가 사이트 정책을 읽어 수행한다. |
| 선행 조건 | 사이트 정책(Phase 5 Control Plane)에 `failure_policy` 필드. 없으면 기본값. |
| 입력 | 저장소 예외, 사이트 `failure_policy` |
| 정상 처리 흐름 | Phase 2: 예외 보고 계약과 기본값. Phase 4: `FAIL_CLOSED` → 503 류 차단 응답, `FAIL_OPEN` → 대기열 생략 통과(토큰 없이 Ingress 가 허용하도록 별도 신호 필요 — Phase 6 토큰 계약과 함께 결정). |
| 예외 처리 | 정책 값이 알 수 없으면 FAIL_CLOSED 로 처리. |
| 상태 변화 | 없음 |
| 권한 | 사이트 정책 변경은 Control Plane RBAC(Phase 5) |
| 네트워크 Zone | DMZ |
| 데이터 저장 위치 | 정책 스냅샷(메모리) |
| 로그/감사 | Fail-open 발동은 감사 대상(Phase 7 경보). |
| 성능 및 동시성 조건 | 명령 타임아웃(`commandTimeout`)이 장애 감지 지연의 상한. |
| 보안 고려사항 | Fail-open 은 공격자가 Redis 를 마비시켜 대기열을 우회하는 경로가 되므로 기본값으로 두지 않고 사이트 운영자가 명시적으로 선택·감사한다. |
| 연관 요구사항 | QUE-008, GAT-*, SEC-11 |

#### 수용 기준

```text
AC-QUE-009-1 Redis 에 연결할 수 없으면 QueueUnavailableException 이 발생하고 메시지에 호스트·자격증명이 포함되지 않는다.
AC-QUE-009-2 사이트 정책이 지정되지 않으면 FailurePolicy.DEFAULT 는 FAIL_CLOSED 다.
AC-QUE-009-3 (Phase 4) Gateway 는 FAIL_CLOSED 사이트에서 저장소 장애 시 신규 입장을 차단한다.
```

#### 테스트

- 단위: `FailurePolicyTest`(AC-QUE-009-2), `RedisConnectionFactoryTest.unreachableHostIsReportedAsUnavailable`(AC-QUE-009-1)
- AC-QUE-009-3 은 Phase 4 Gateway 테스트
