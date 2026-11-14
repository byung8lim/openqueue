# CHECKPOINT — 단위 작업 재개 지점

> 세션 재개 시 **이 파일만** 읽고 `doing` 또는 첫 `todo` 행부터 이어서 작업한다. 단위 작업마다 즉시 갱신. 절차: `docs/claude.md` §3.4

- 갱신: 2026-11-15
- Phase: **4 — Edge Gateway·동적 라우팅** (TASK-007 로 묶어 보고 예정). Phase 3 은 TASK-006 보고서로 종료, CI E2E 확인만 남음.
- 검증 상태: 미착수. 테스트 실행은 Phase 게이트(P4-08)에서 1회.

## 단위 작업

| ID | 내용 | 상태 | 산출 파일 |
|---|---|---|---|
| P4-00 | Phase 3 CI 결과 확인(frontend-test·frontend-e2e) → 추적표 E2E 열·PROGRESS % 갱신 | todo | docs/test/traceability-matrix.md |
| P4-01 | SRS GAT-001~004 상세 명세·AC + 결정 D-GAT-xxx(신뢰 프록시 IP, Fail-open 분기 위치, 정적 자원 경로) + ADR(Spring Cloud Gateway 버전) | todo | docs/requirements/SRS.md, docs/adr/ |
| P4-02 | `openqueue-gateway` 모듈 스캐폴딩(Spring Cloud Gateway, enforcer: DB·control-api 금지, queue-api 의존 금지) | todo | backend/openqueue-gateway/, backend/pom.xml |
| P4-03 | Host/path 기반 사이트·서비스 식별 + 대기열 활성/비활성 분기 필터(활성이면 /queue 로 리다이렉트, 입장 토큰 없으면 차단) | todo | gateway/filter/ |
| P4-04 | 서비스 ID 라우팅 + 목적지 allowlist(host/port/scheme) + DNS 해석 IP 검증(loopback/link-local/metadata/사설 대역 차단) | todo | gateway/routing/ |
| P4-05 | 런타임 라우트 원자 교체(설정 스냅샷 → AtomicReference, Phase 6 서명 스냅샷 전 단계) | todo | gateway/routing/ |
| P4-06 | timeout/circuit breaker, 요청 ID 전파·구조화 로그(OBS-001), Queue API 로의 /queue 프록시(쿠키 Secure 전제) | todo | gateway/ |
| P4-07 | 테스트 코드: 필터·라우팅·SSRF 반대 케이스·라우트 교체 동시성(WebTestClient/MockServer) | todo | gateway/src/test |
| P4-08 | **Phase 게이트**: `mvn verify` 1회 + 수정 반복 | todo | — |
| P4-09 | 보고서 TASK-007 · 추적표 · PROGRESS(% 갱신) · SRS 상태 · 커밋 | todo | docs/reports/TASK-007-report.md 등 |

## 다음 할 일

P4-00 → P4-01 순. P4-01 에서 결정은 기록 후 바로 구현(사후 승인).

## 미결 (막히지 않음)

- 승인 묶음: D-QUE-*, D-API-001~006, SpotBugs 제외 2건 (TASK-006 보고서 §12)
- 기존 API Gateway/LB Ed25519 지원 여부 — Phase 6 전
