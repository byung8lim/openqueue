# ADR-0008: 정책·라우트 스냅샷의 DMZ 전달 — Queue Redis 경유 Push (DEC-02)

- 상태: **기각(소유자 2026-11-14)** — "Redis 큐에서 탈취 가능"을 이유로 Redis 경유 방식을 쓰지 않기로 함. 대체: [ADR-0009](ADR-0009-dmz-control-plane-interface.md)
- 이 문서는 검토 기록으로만 보존한다. 아래 조건·분석은 구현 근거가 아니다.
- 영향도 등급: I4 (Zone 간 통신 A6 변경, Redis ACL 계정 추가)
- 관련 요구사항: POL-001, POL-002, POL-003, POL-004, GAT-004, NET-003, QUE-009

## 1. 소유자 결정

내부 관리망의 정책 배포 서비스가 **서명된 정책 스냅샷을 DMZ Queue Redis에 기록**하고,
DMZ Queue Engine은 **Redis 구독(listener)으로 변경을 감지**해 검증·적용한다. HTTP 수신 포트는 두지 않는다.

## 2. 설계 적합성 판정 — 설계는 틀어지지 않는다

설계서(§4.4)와 개발요청서(§4.2, §4.4, §11)가 정책 전달에 요구하는 것은 네 가지다.

| 요구 | Redis 경유 방식에서의 충족 |
|---|---|
| 정책은 내부에서 생성·검증·서명 | 변함없음. Redis에 넣기 전에 관리망에서 서명한다 |
| DMZ는 서명·발행자·버전·스키마·유효기간·allowlist를 검증한 뒤 원자 교체 | Engine이 Redis에서 읽은 바이트를 검증한다. **Redis 내용 자체는 신뢰하지 않는다** |
| 실패 시 마지막 정상 정책 유지 | 메모리의 현재 정책을 유지하고 오류 상태를 보고한다 |
| Zone 간 연결은 내부에서 시작, DMZ → 관리망 신규 연결 없음 | 쓰기 연결은 관리망 → DMZ Redis. DMZ는 자기 Zone 안의 Redis만 읽는다 |

즉 Redis는 **전달 통로**이고 신뢰의 근거는 **서명**이다. 이는 open-decisions §3.1의 선택지 C("DMZ 저장소 경유")에 해당한다.
단, Queue Redis는 DMZ(침해 가정)에 있으므로 §3의 조건을 전제로만 채택한다.

## 3. 필수 조건

### 3.1 신뢰 경계

1. **공개키(서명 검증 키)는 Redis로 배포하지 않는다.** 배포 설정(파일·환경변수·K8s Secret)으로 Engine에 주입한다.
   Redis가 장악되면 검증 키와 정책을 함께 바꿔 넣어 위조 정책을 통과시킬 수 있기 때문이다. 키 교체는 ADR-0005의 `kid` 방식으로 한다.
2. **재생·롤백 공격 방지**: 스냅샷 `version`은 단조 증가하는 정수(또는 발행 시각+순번)이고, Engine은
   현재 적용 버전 **이하**의 버전을 거부한다. `not_before`/`not_after`를 검증한다. 운영상 롤백은 "이전 내용을 담은 **새 버전**"으로만 한다(개발요청서 §11).
3. 정책 payload에는 `target_env`(예: `prod`)를 넣고 Engine 설정의 환경과 일치해야 적용한다(다른 환경 스냅샷 오배포 방지).

### 3.2 전달·유실 처리

4. **상태는 key에, Pub/Sub은 알림에만** 쓴다. Pub/Sub은 저장하지 않으므로 수신자가 꺼져 있으면 메시지가 사라진다.
   - `oq:policy:current` — 현재 서명 스냅샷(불투명 바이트, 서명 포함) 1개
   - `oq:policy:events` — 채널. 새 버전 번호만 publish
   - Engine: 기동·재접속 시 `current`를 읽고, 알림을 받으면 `current`를 다시 읽는다. 알림 유실 대비로 **주기적 버전 확인**(예: 30초)을 병행한다.
5. **key 삭제·Redis 초기화·failover 유실**에도 Engine은 메모리의 마지막 정상 정책을 유지한다.
   콜드 스타트를 위해 마지막 정상 스냅샷을 **서명 포함 로컬 캐시 파일**로 보관하고, 기동 시 Redis가 비어 있으면 캐시를 검증 후 사용한다.
   캐시도 없으면 **Fail-closed**(DEC-09): 대기열 비활성·입장 발급 중단 상태로 기동하고 경보한다.
6. Redis에는 **현재 적용본 1개만** 둔다. 이력·승인 기록·정본은 MariaDB(개발요청서 §11 "Redis를 영속 정책 DB로 사용하지 않는다").
7. Redis 3모드(ADR-0004): Cluster의 Pub/Sub은 전체 노드 브로드캐스트지만 정책 알림은 저빈도라 허용한다.
   Redis 7 이상에서는 sharded Pub/Sub(`SPUBLISH`/`SSUBSCRIBE`) 사용을 검토한다. 정책 key는 대기열 key와 hash slot을 공유할 필요가 없으므로 hash tag를 쓰지 않는다.

### 3.3 계정·네트워크 분리

8. **Redis ACL 계정 2종 분리**(NET-003):
   - `policy-publisher`(관리망 배포 서비스): `oq:policy:*` key에 `SET`/`GET`, `oq:policy:events` 채널에 `PUBLISH`만. 대기열 key 접근 불가.
   - Queue Engine 계정: `oq:policy:current` `GET`, 채널 `SUBSCRIBE`, 자기 노드 상태 key `oq:policy:status:<node>` 쓰기, 대기열 key 읽기·쓰기. **정책 key 쓰기 불가.**
9. **포트 매트릭스 A6 변경**: 출발지 정책 배포 서비스(관리망) → 목적지 Queue Redis(DMZ 전용 서브넷) Redis+TLS 6380.
   방화벽은 배포 서비스의 고정 IP만 허용한다. Redis 포트가 DMZ 밖(관리망)에서 접근 가능해지는 것이므로 **보안·네트워크팀 승인 필요**.
10. 배포 서비스의 Redis 자격증명은 관리망 Secret에 두고, DMZ 어디에도 저장하지 않는다.

### 3.4 Gateway로의 전달

라우트 스냅샷(ADR-0007)도 같은 통로로 전달된다. Gateway(DMZ)가 이를 받는 방법 두 가지:

| 방법 | 평가 |
|---|---|
| **(a) Queue Engine 중계 (권장)** — Gateway가 기존 A3(mTLS)로 Engine에서 현재 스냅샷을 받고, Engine이 변경 알림을 준다 | 새 통신 행 없음. Gateway는 Redis 자격증명을 갖지 않아 ADR-0004 §1 원칙 유지. Gateway도 서명을 **직접** 검증한다(Engine을 신뢰하지 않음) |
| (b) Gateway가 읽기 전용 ACL로 Redis를 직접 구독 | 통신 행(A11)과 Redis 계정이 추가된다. Gateway 침해 시 Redis 접근 경로가 생긴다 |

(a)를 기본안으로 한다. Gateway의 Redis 의존성 추가는 ADR-0003 금지 목록에 올린다.

### 3.5 노드별 상태 보고 (POL-003)

Engine은 적용 결과(`applied_version`, `last_error`, `updated_at`)를 `oq:policy:status:<node_id>`에 기록한다.
관리망은 이 key를 **읽기만** 하고 표시용으로만 쓴다(DMZ가 쓴 데이터이므로 신뢰하지 않으며, 제어 판단에 사용하지 않는다).

## 4. 대안 비교

| 대안 | 장점 | 단점 |
|---|---|---|
| A. HTTP Push(mTLS) | 전달 성공을 응답으로 즉시 확인 | DMZ 노드마다 관리망이 접속하는 수신 포트 필요, 노드 수만큼 전송 |
| **C. Redis 경유 (채택)** | 수신 포트 불필요, 노드 수 무관, 기존 Redis 재사용 | Redis 포트의 관리망 노출(A6), 전달 확인이 간접적(상태 key), §3 조건 필수 |
| D. 단방향 중계 장비 | 가장 강한 격리 | 장비·비용, 상태 보고 역방향 통로 별도 필요 |

Redis가 정책 전달의 단일 장애점이 되지만 대기열 자체가 이미 Redis에 의존하므로 가용성 요구는 추가되지 않는다.

## 5. 결과·후속

- port-matrix A6, zone-security-policy A6 갱신(이 ADR 참조). SRS에 POL-004(Redis 경유 전달 규칙) 추가.
- Phase 6에서 구현. 테스트 게이트: 서명 위조·구버전 재투입·key 삭제·Redis 초기화 후 기동(캐시/Fail-closed)·ACL 위반 명령 거부.
- 보안팀 검토에서 A6 Redis 노출이 불허되면 대안 A(HTTP Push)로 전환한다. Engine의 정책 적용 로직은 전달 통로와 분리해 두어 전환 비용을 낮춘다.
