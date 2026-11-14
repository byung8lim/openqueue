# ADR-0009: DMZ ↔ 내부망 Control Plane 인터페이스 (DEC-02, DEC-06)

- 상태: **승인(소유자 2026-11-14)** — 전송 방향·포트(§4)는 보안·네트워크팀 검토 권장
- 영향도 등급: I4 (Zone 간 통신 A6 정의, 내부 API 노출 범위)
- 대체: ADR-0008(Redis 경유, 기각)
- 관련 요구사항: POL-001~004, GAT-004, NET-002/004/005, QUE-009

## 1. 소유자 결정

1. **DEC-06**: Gateway가 필요로 하는 인터페이스(라우트 등)는 **내부의 API 서버가 중개**한다. Gateway는 DB에 접속하지 않는다.
2. **DEC-02**: Redis 큐는 탈취 위험이 있어 사용하지 않는다. 정책 전달은 DEC-06 요건까지 충족하는
   **DMZ ↔ 내부망 양방향 통신 인터페이스**를 추가해 처리한다.

## 2. 결정 — Distribution API

내부 관리망에 **Distribution API**(배포 중개 서버)를 둔다. DMZ 구성요소(Edge Gateway, Queue Engine)가 필요로 하는 모든
Control Plane 데이터는 이 하나의 인터페이스로만 오간다. Admin용 Control API와는 **별도 프로세스·별도 포트**다.

```text
[Admin Web] → [Control API] → MariaDB (정본)
                                  ↓ 승인 → 불변 스냅샷 생성 + 서명
                          [Distribution API]  (관리망, 별도 프로세스, 스냅샷 읽기 전용 DB 계정)
                                  ↕  A6: mTLS, 노드별 클라이언트 인증서
[Edge Gateway] [Queue Engine]  (DMZ)  — 서명 검증 → 메모리 원자 교체, 적용 상태 보고
```

### 2.1 인터페이스 (계약 초안, Phase 6에서 OpenAPI로 확정)

| 방향 | 작업 | 설명 |
|---|---|---|
| DMZ → 내부 | `GET /dist/v1/snapshots/{kind}/current` (`kind` = `routes` \| `queue-policy`) | 현재 서명 스냅샷. `If-None-Match: <version>` 이면 변경 없을 때 304 |
| 내부 → DMZ | `GET /dist/v1/events` (SSE) | 새 버전 발행·키 교체 예고 등 **서버 푸시** 알림. 수신 시 DMZ가 `current`를 다시 가져온다 |
| DMZ → 내부 | `PUT /dist/v1/nodes/{node_id}/status` | 노드별 적용 버전·마지막 오류·시각 (POL-003). 표시용, 제어 판단에 쓰지 않음 |
| DMZ → 내부 | `GET /dist/v1/keys` | 정책 서명 공개키 집합(JWKS, `kid` 포함). 초기 공개키는 배포 설정으로 주입하고 교체분만 이 경로로 받는다(§3-2) |

"양방향"은 **연결 하나(DMZ → 관리망, mTLS) 위에서 데이터가 양쪽으로 흐르는** 구조다. 관리망이 DMZ 노드로 연결을 여는 방향은 두지 않는다
(DMZ 노드마다 수신 포트를 열 필요가 없고, 노드 수·배포 형태(ADR-0004 §3)에 무관하다).

### 2.2 DMZ 측 동작 (Gateway·Queue Engine 공통, `openqueue-policy` 모듈)

1. 기동: 로컬 캐시(서명 포함 마지막 정상 스냅샷)를 검증 후 적용 → Distribution API에서 `current` 조회 → 더 새 버전이면 교체.
   캐시도 없고 API도 실패하면 **Fail-closed**(DEC-09): 대기열 비활성·입장 발급 중단으로 기동하고 경보.
2. 운영: SSE 알림 수신 시 `current` 재조회. 알림 유실 대비 **주기적 조회**(예: 30초, `If-None-Match`) 병행.
3. 적용: 서명·발행자·버전(단조 증가, 현재 이하 거부)·스키마·유효기간·`target_env`·목적지 allowlist 검증 → 원자 교체 → 실패 시 기존 유지 + 상태 보고.
4. Gateway는 받은 라우트 스냅샷에서 `RouteDefinitionLocator` 메모리 테이블을 갱신하고 `RefreshRoutesEvent`로 반영한다(ADR-0007). 재기동 없음.

## 3. 보안 조건 — DMZ 침해를 가정한 노출 범위 제한

Distribution API는 DMZ에서 접속하는 **유일한 내부 API**이므로 다음을 필수로 한다.

1. **Admin Control API와 분리**: 별도 실행 산출물·포트·서비스 계정. 관리 기능(CRUD, 승인, 사용자)은 전혀 노출하지 않는다. 읽기(스냅샷·키)와 상태 쓰기만.
2. **서명은 그대로 유지**: mTLS가 있어도 스냅샷은 서명하고 DMZ가 검증한다(개발요청서 §4.2 절대 조건, 심층 방어, 로컬 캐시 신뢰 근거).
   초기 공개키는 API가 아닌 배포 설정으로 주입한다 — API가 키와 정책을 함께 주면 API 탈취 시 위조가 가능해진다. 교체 키는 **현재 키로 서명된** 키 집합만 수용한다.
3. **mTLS + 노드 식별**: 노드별 클라이언트 인증서, 인증서 식별자 allowlist, 인증서 폐기 시 즉시 거부. 상태 보고는 자기 `node_id`만 쓸 수 있다.
4. **DB 계정**: 스냅샷·발행 키 테이블 읽기 + 노드 상태 테이블 쓰기만 가능한 전용 계정. 정책 원본·관리자·감사 테이블 접근 불가. DMZ 침해 시 얻는 것은 "이미 DMZ로 갈 서명본"뿐이다.
5. **남용 제한**: 노드당 요청 rate limit, 응답 크기 상한, 감사 로그(노드, 버전, 결과). SSE 연결 수 상한.
6. **방화벽**: 출발지 DMZ Gateway·Queue Engine 서브넷 → 목적지 Distribution API 포트만. Admin Control API·MariaDB 포트는 DMZ에서 계속 차단(D2, D3).
7. 토큰 서명 요청(A10, ADR-0005)은 이 인터페이스에 **합치지 않는다**. 서명 서비스는 별도 프로세스로 두어 Distribution API 침해가 서명 능력으로 이어지지 않게 한다.

## 4. 대안 비교

| 대안 | 평가 |
|---|---|
| **DMZ → 관리망 단일 연결 + SSE (채택)** | 수신 포트 불필요, 노드 수 무관, 전달 확인 즉시. DMZ → 관리망 포트 1개 노출(§3으로 제한) |
| 관리망 → DMZ Push(HTTP) | DMZ 노드마다 수신 포트, 노드 목록 관리 필요. K8s 등 동적 환경에서 불리 |
| Queue Redis 경유 (ADR-0008) | 소유자 기각 — Redis 탈취 시 정책 전달 통로가 노출됨 |
| Gateway가 DB 직접 조회 | §4.2 절대 조건 위반 |

## 5. 결과·후속

- port-matrix A6 재정의(DMZ → Distribution API 9444 mTLS), zone-security-policy 갱신, SRS POL-004·GAT-004 갱신.
- 모듈: 서버는 `openqueue-distribution-api`(신규, 관리망), 클라이언트·검증은 `openqueue-policy`. Phase 6 착수 시 module-design.md와 enforcer 규칙 갱신
  (DMZ 모듈은 `distribution-api` 서버 모듈에 의존 불가).
- Phase 6 테스트 게이트: 서명 위조·구버전 재투입·API 불가 시 캐시 기동·캐시 없음 Fail-closed·타 노드 상태 쓰기 거부·관리 경로 미노출 확인.
