# 진행 현황 · 재개 지점 (PROGRESS)

> 작업을 중단·재개할 때 가장 먼저 읽는 문서. 매 작업 종료 시 갱신한다.

- 마지막 갱신: **2026-11-14 09:15** (오전 작업 종료)
- 현재 Phase: **Phase 1 — Queue Domain Core** (진행 중)
- 마지막 완료 작업: **TASK-002** (QUE-003 세션 생성·조회·취소, QUE-004 만료·재접속) — 단위 테스트 50/50, `clean verify` 통과
- 저장소: https://github.com/byung8lim/openqueue (`main`, CI 통과 확인: TASK-001)

## 1. Phase 진행표

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 개발 기반·설계 확정 | **완료** (TASK-000, TASK-001) |
| 1 | Queue Domain Core | **진행 중** — QUE-001/002/003/004 완료, QUE-005/006/007 남음 |
| 2 | Redis Queue Adapter | 미착수 (Docker 필요 → 서버 환경에서 통합 테스트) |
| 3~8 | Waiting Room API / Gateway / Control Plane / 정책·토큰 / HA / 보안 검증 | 미착수 |

## 2. 다음에 할 일 (순서대로)

1. **TASK-003**: QUE-005 동시 입장 상한 기반 입장 가능 인원 계산 + QUE-007 입장 lease·heartbeat
   - 명세: SRS §3에 QUE-005/007 상세(AC) 작성 → 테스트(Red) → 구현(Green)
   - 설계 메모: `QueuePolicy`에 `concurrencyLimit`, `admissionLeaseTtl` 추가. ADMITTED 세션에 lease 만료 시각을 두고
     heartbeat 로 연장, 만료 시 `EXPIRED`(또는 별도 상태)로 전이해 슬롯 회수. `availableSlots = limit - 활성 ADMITTED 수`.
     `admit(requestId, now)` 오버로드(슬롯 자동 계산) 추가. QUE-003의 ADMITTED 취소는 lease 해제로 연결.
2. **TASK-004**: QUE-006 초당/분당 입장률 제한 — `QueuePolicy`에 `admissionRate(maxPerWindow, window)`; 최근 입장 시각 창(window) 기준 계산.
3. Phase 1 종료 보고 → Phase 2 착수 전 결정: Redis key 설계 문서(`docs/database/redis-keys.md`), Lua 스크립트 계약.

## 3. 열린 확인 사항 (막히지 않음, 답 오면 반영)

- 내장 Admin 계정 MFA(TOTP) 요구 여부 — Phase 5 전 (ADR-0006)
- 기존 API Gateway/LB의 Ed25519 지원 여부 — Phase 6 전 (ADR-0005)
- A6/A10 DMZ → 관리망 통신 보안·네트워크팀 검토 (ADR-0009, ADR-0005)
- Dependabot PR 9건 처리 방침 (TASK-001 보고서 §12) — 메이저(JUnit 6, Action 메이저)는 로컬 검증 후 병합 권고
- GitHub 설정: `main` 브랜치 보호, Private vulnerability reporting 활성화 (사람이 수행)

## 4. 재개 절차

```bash
git pull
bash scripts/verify.sh          # 50/50, BUILD SUCCESS 확인
# 그 다음 docs/requirements/SRS.md §3 에 QUE-005/007 명세 추가 → TDD
```

- 개발 규칙: `CLAUDE.md`, `docs/claude.md`(§2 원칙, §3.3 보고 형식, §13 DoD)
- 결정 현황: `docs/decisions/open-decisions.md`
- 환경 제약: 로컬(Windows IDE)은 단위 테스트·`mvn verify`·CI까지만. 통합·배포 검증은 소스 개발 완료 후 Linux 서버에서.
