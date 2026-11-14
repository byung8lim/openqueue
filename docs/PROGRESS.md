# 진행 현황 · 재개 지점 (PROGRESS)

> 작업을 중단·재개할 때 가장 먼저 읽는 문서. 매 작업 종료 시 갱신한다.

- 마지막 갱신: **2026-11-15** (TASK-006 종료 · **Phase 3 로컬 게이트 통과**, CI frontend-e2e 확인 대기)
- 현재 Phase: **Phase 4 — Edge Gateway·동적 라우팅** (착수 전, 단위 작업 목록은 `docs/CHECKPOINT.md`)
- 마지막 완료 작업: **TASK-006** (QUE-010~013, WUI-001/002) — mvn verify 통과(JUnit testcase 106건), Vitest 22건 통과, Playwright E2E 4건은 CI 전용
- 저장소: https://github.com/byung8lim/openqueue (`main`). CI 전부 성공: https://github.com/byung8lim/openqueue/actions/runs/43571183051
- 절차: 2026-11-15 부터 **토큰 효율 절차**(`docs/claude.md` §3.4) — 단위 작업은 테스트 코드+구현만 작성하고 실행 없음, `mvn verify` 는 Phase 말 1회, 단위 작업마다 `docs/CHECKPOINT.md` 갱신, 보고서는 Phase 당 1건. 결정은 기록 후 구현(사후 승인).

## 0. 전체 진행률: **약 37 %**

산정 방식: Phase별 작업 항목 수(개발요청서 §9)를 예상 난이도로 가중하고, 완료 항목 비율을 곱해 합산한다.
매 TASK 종료 보고에 이 수치를 갱신한다. Phase 2 는 "테스트 게이트(통합) 통과" 1항목을 CI 확인 전까지 미완료로 둔다.

| Phase | 가중치 | 항목 수 | 완료 | 기여 |
|---|---|---|---|---|
| 0 기반·설계 | 8 % | 9 | 9 | 8.0 |
| 1 Queue Domain | 8 % | 7 | 7 | 8.0 |
| 2 Redis Adapter | 10 % | 7 | 7 | 10.0 |
| 3 Waiting Room·API | 14 % | 5 | 4 | 11.2 |
| 4 Gateway·라우팅 | 14 % | 7 | 0 | 0 |
| 5 Control Plane·Admin | 16 % | 6 | 0 | 0 |
| 6 정책 배포·토큰 | 12 % | 7 | 0 | 0 |
| 7 HA·운영 | 10 % | 8 | 0 | 0 |
| 8 보안 검증·릴리스 | 8 % | 8 | 0 | 0 |
| **합계** | 100 % | 64 | 27 | **37.2** |

Phase 2 항목: Redis TLS/ACL 연결 ✔, key namespace ✔, Lua 원자 연산 ✔, 등록·조회·삭제 ✔, TTL/lease ✔, 장애·재시도 처리 ✔, 통합 테스트 게이트 ✔ (CI redis-integration 통과).
Phase 3 항목: 대기 등록·조회·취소 API ✔, SSE 갱신 ✔, React Waiting Room ✔, 세션 복구·만료 안내 ✔, rate limit ✔ — 테스트 게이트(Playwright E2E) 는 CI 확인 전까지 1항목 미완료로 둔다.

## 1. Phase 진행표

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 개발 기반·설계 확정 | **완료** (TASK-000, TASK-001) |
| 1 | Queue Domain Core | **완료** (TASK-002~004) — QUE-001~007, 단위 76건. 명세 `검토중`(승인 대기) |
| 2 | Redis Queue Adapter | **완료** (TASK-005) — QUE-008/009, 단위 9건 + 통합 15건(CI Redis 7) 통과. 명세 `검토중`(승인 대기) |
| 3 | Waiting Room·Queue API | **완료(CI E2E 확인 대기)** (TASK-006) — QUE-010~013, WUI-001/002. 단위·Vitest 로컬 통과. 명세 `검토중` |
| 4~8 | Gateway / Control Plane / 정책·토큰 / HA / 보안 검증 | 미착수 |

## 2. 다음에 할 일 (순서대로)

1. ~~CI 확인~~ 완료: redis-integration 통과, 추적표 갱신됨.
2. **CI 확인**: frontend-test·frontend-e2e 잡 최초 실행 결과 확인(실패 시 lease/interval 타이밍 조정), 추적표 E2E 열 갱신.
3. **TASK-007 (Phase 4)**: `openqueue-gateway` (Spring Cloud Gateway 2025.0.x, Boot 3.5 호환 버전 ADR) — Host/path 사이트 식별, 대기열 활성/비활성 분기,
   서비스 ID 라우팅·목적지 allowlist·SSRF/DNS rebinding 방어, 런타임 라우트 원자 교체(설정 기반), timeout/circuit breaker, 요청 ID·구조화 로그.
   단위 작업은 `docs/CHECKPOINT.md` P4-01~.
4. Phase 4 전 결정: 신뢰 프록시 IP 추출 방식, Fail-open 분기 위치(D-API-004 전제), /queue·Waiting Room 정적 자원 제공 경로.
5. 승인 묶음 요청(막히지 않음): D-QUE-001~007, D-QUE-008-1~7, 포트 위치, D-QUE-007-2(`LEASE_EXPIRED`), D-QUE-006-2, **D-API-001~006, SpotBugs 제외 2건**.

## 3. 열린 확인 사항 (막히지 않음, 답 오면 반영)

- 위 §2-5 승인 묶음
- DEC-05 실시간 갱신 방식: SSE 권고안 — Phase 3 TASK-007 전
- 내장 Admin 계정 MFA(TOTP) 요구 여부 — Phase 5 전 (ADR-0006)
- 기존 API Gateway/LB의 Ed25519 지원 여부 — Phase 6 전 (ADR-0005)
- A6/A10 DMZ → 관리망 통신 보안·네트워크팀 검토 (ADR-0009, ADR-0005)
- Dependabot PR 처리 방침 — 메이저는 로컬 검증 후 병합. lettuce/testcontainers 갱신은 CI 통합 테스트 통과 조건
- GitHub 설정: `main` 브랜치 보호, Private vulnerability reporting 활성화 (사람이 수행)

## 4. 재개 절차

```bash
git pull
# docs/CHECKPOINT.md 를 읽고 doing/첫 todo 행부터 이어서 작업 (테스트는 Phase 게이트에서 1회)
```

- 개발 규칙: `CLAUDE.md`, `docs/claude.md`(§2 원칙, §3.3 보고 형식, §13 DoD). Phase 2 부터 간소화 절차(위 머리말)
- 결정 현황: `docs/decisions/open-decisions.md`, Redis 계약: `docs/database/redis-keys.md`
- 환경 제약: 로컬(Windows IDE)은 단위 테스트·`mvn verify`까지. Docker 통합 테스트는 CI(`-Pintegration`), 배포 검증은 Linux 서버.
