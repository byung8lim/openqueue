# ADR-0007: 런타임 라우트 관리 (DEC-06)

- 상태: **승인(소유자 2026-11-14)** — "내부의 API 서버가 Gateway가 필요한 인터페이스를 중개한다"로 확정. 전달 인터페이스는 ADR-0009
- 영향도 등급: I4 (DMZ↔DB 경계 해석)

## 1. 소유자 결정

- Gateway는 **Spring Cloud Gateway**를 사용한다.
- 라우트는 YAML에 두지 않고 **실행 시 DB로부터 로딩**하며, **운영 중 추가·수정**할 수 있어야 한다.

## 2. 해석안 — 원천은 DB, Gateway는 서명 스냅샷으로 로딩

개발요청서 §4.2는 "DMZ에서 MariaDB 접속을 허용하지 않는다"를 절대 조건으로 둔다. Gateway는 DMZ에 있으므로
Gateway가 DB에 직접 접속하는 방식은 이 조건과 충돌한다. 결정 의도를 유지하는 해석안은 다음과 같다.

```text
[Admin 화면] 라우트 추가/수정
   → [Control API] 검증(서비스 ID 존재, 목적지 allowlist, 경로 충돌) → MariaDB 저장
   → 승인 → 불변 버전 스냅샷 생성 + 서명
   → Distribution API(ADR-0009, 내부 API 서버가 중개)로 DMZ Gateway 노드에 전달
   → [Gateway] 서명·버전·스키마·allowlist 검증 → 메모리 라우트 테이블 원자 교체 (재기동 없음)
              실패 시 기존 라우트 유지 + 오류 보고
```

| 결정 의도 | 해석안에서 충족 방법 |
|---|---|
| YAML이 아님 | `application.yml`에는 서버 기본 설정만. 라우트 정의 없음 |
| 실행 시 로딩 | 기동 시 로컬에 보관된 마지막 정상 스냅샷 로딩, 이후 새 버전 수신 시 즉시 교체 |
| 추가·수정 가능 | Admin 화면 → DB → 스냅샷 배포. 재기동 없이 반영 |
| 원천이 DB | 라우트의 정본(source of truth)은 내부 MariaDB |
| DMZ DB 접근 금지 | Gateway 프로세스는 DB 계정·드라이버를 갖지 않는다(ADR-0003 enforcer로 강제) |

구현 후보: 사용자 정의 `RouteDefinitionLocator` + 메모리 라우트 테이블 + `RefreshRoutesEvent`.
라우트 목적지는 URL이 아닌 `service_id` 참조만 허용한다(GAT-002/003).

## 3. 대안 (채택 시 보안 예외 승인 필요)

| 대안 | 문제 |
|---|---|
| Gateway가 내부 MariaDB를 직접 조회 | §4.2 절대 조건 위반. DMZ 침해 시 DB 접속정보 노출 |
| DMZ 안에 Gateway 전용 DB를 별도 운영 | 허용 가능하나 동기화·서명 검증 문제가 그대로 남고 구성요소만 늘어남 |

## 4. 소유자 확인 결과 (2026-11-14)

"실행 시 DB로부터 로딩"은 **내부 API 서버(Distribution API)가 DB를 읽어 Gateway에 중개**하는 의미로 확정되었다.
Gateway는 DB 계정·드라이버를 갖지 않으며(ADR-0003), 기동 시와 변경 시 ADR-0009 인터페이스로 서명 라우트 스냅샷을 받아 메모리 교체한다.
