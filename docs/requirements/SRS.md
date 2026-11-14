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
| QUE-002 | Site/Queue 식별자 모델 및 입력 검증 | DEC-12 | 1 | 초안 (QUE-001에서 최소 범위 선구현) |
| QUE-003 | 대기 세션 생성·조회·취소 | 9.3 | 1 | 초안 |
| QUE-004 | 세션 만료(TTL) 및 재접속 복구 규칙 | 9.3, 8.2 | 1 | 초안 |
| QUE-005 | 동시 입장 상한 기반 입장 가능 인원 계산 | 7.3 | 1 | 초안 |
| QUE-006 | 초당/분당 입장률 제한 | 9.3 | 1 | 초안 |
| QUE-007 | heartbeat 및 입장 lease 관리 | 7.3 | 1, 2 | 초안 |
| QUE-008 | Redis Lua 기반 원자적 등록·입장 할당. Standalone/Sentinel/Cluster 공통 key 설계(hash tag), failover 초과 입장 방지 | 7.3, ADR-0004 | 2 | 초안 |
| QUE-009 | 사이트별 Fail-open/Fail-closed 정책 적용. **기본값 Fail-closed**, 사이트별 변경 가능 | SEC-11, 12.2, DEC-09 | 2 | 초안 (방향 결정됨) |

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
