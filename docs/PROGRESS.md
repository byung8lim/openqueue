# 진행 현황 · 재개 지점 (PROGRESS)

> 작업을 중단·재개할 때 가장 먼저 읽는 문서. 매 작업 종료 시 갱신한다.

- 마지막 갱신: **2026-11-14 15:50** (오후 작업, TASK-003 종료)
- 현재 Phase: **Phase 1 — Queue Domain Core** (진행 중, 잔여 QUE-006 1건)
- 마지막 완료 작업: **TASK-003** (QUE-005 동시 입장 상한, QUE-007 입장 lease·heartbeat) — 단위 테스트 65/65, `clean verify` 통과
- 저장소: https://github.com/byung8lim/openqueue (`main`, CI 통과 확인: TASK-001. TASK-002/003 커밋의 CI 결과는 다음 세션에서 확인)

## 0. 전체 진행률: **약 15 %**

산정 방식: Phase별 작업 항목 수(개발요청서 §9)를 예상 난이도로 가중하고, 완료 항목 비율을 곱해 합산한다.
매 TASK 종료 보고에 이 수치를 갱신한다. (가중치는 추정값이며 Phase 2 착수 시 재조정)

| Phase | 가중치 | 항목 수 | 완료 | 기여 |
|---|---|---|---|---|
| 0 기반·설계 | 8 % | 9 | 9 | 8.0 |
| 1 Queue Domain | 8 % | 7 | 6 | 6.9 |
| 2 Redis Adapter | 10 % | 6 | 0 | 0 |
| 3 Waiting Room·API | 14 % | 5 | 0 | 0 |
| 4 Gateway·라우팅 | 14 % | 7 | 0 | 0 |
| 5 Control Plane·Admin | 16 % | 6 | 0 | 0 |
| 6 정책 배포·토큰 | 12 % | 7 | 0 | 0 |
| 7 HA·운영 | 10 % | 8 | 0 | 0 |
| 8 보안 검증·릴리스 | 8 % | 8 | 0 | 0 |
| **합계** | 100 % | 63 | 15 | **14.9** |

## 1. Phase 진행표

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 개발 기반·설계 확정 | **완료** (TASK-000, TASK-001) |
| 1 | Queue Domain Core | **진행 중** — QUE-001/002/003/004/005/007 완료, QUE-006 남음 |
| 2 | Redis Queue Adapter | 미착수 (Docker 필요 → 서버 환경에서 통합 테스트) |
| 3~8 | Waiting Room API / Gateway / Control Plane / 정책·토큰 / HA / 보안 검증 | 미착수 |

## 2. 다음에 할 일 (순서대로)

1. **TASK-004**: QUE-006 초당/분당 입장률 제한
   - 명세: SRS §3에 QUE-006 상세(AC) 작성 → 테스트(Red) → 구현(Green)
   - 설계 메모: `QueuePolicy`에 `admissionRate(maxPerWindow, window)` 추가(선택 가능하게 할지 결정 필요 — 기본값 없음 원칙 D-QUE-004-3).
     최근 입장 시각 창(window) 기준으로 이번 주기 허용 인원을 계산해 `admit(requestId, requestedSlots, now)` 의
     `requestedSlots` 로 넘긴다(D-QUE-005-1 결합 방식). 입장 시각 목록은 `admitted` 집합의 `last_seen_at` 과 분리해 별도 기록 필요
     (heartbeat 가 `last_seen_at` 을 갱신하므로).
2. Phase 1 종료 보고 → 소유자 결정 요청: D-QUE-005-1~3, D-QUE-007-1~4(특히 D-QUE-007-2 `LEASE_EXPIRED` 분리 여부).
3. Phase 2 착수 전 결정: Redis key 설계 문서(`docs/database/redis-keys.md`), Lua 스크립트 계약.
   설계 메모: 대기 ZSET + 입장(lease) ZSET, score = `last_seen_at`. 도메인 모델과 1:1 대응.

## 3. 열린 확인 사항 (막히지 않음, 답 오면 반영)

- D-QUE-007-2: lease 만료 상태를 `EXPIRED` 로 합칠지 `LEASE_EXPIRED` 로 분리할지 — Phase 3 전 (지금 바꾸면 비용 작음)
- 내장 Admin 계정 MFA(TOTP) 요구 여부 — Phase 5 전 (ADR-0006)
- 기존 API Gateway/LB의 Ed25519 지원 여부 — Phase 6 전 (ADR-0005)
- A6/A10 DMZ → 관리망 통신 보안·네트워크팀 검토 (ADR-0009, ADR-0005)
- Dependabot PR 9건 처리 방침 (TASK-001 보고서 §12) — 메이저(JUnit 6, Action 메이저)는 로컬 검증 후 병합 권고
- GitHub 설정: `main` 브랜치 보호, Private vulnerability reporting 활성화 (사람이 수행)

## 4. 재개 절차

```bash
git pull
bash scripts/verify.sh          # 65/65, BUILD SUCCESS 확인
# 그 다음 docs/requirements/SRS.md §3 에 QUE-006 명세 추가 → TDD
```

- 개발 규칙: `CLAUDE.md`, `docs/claude.md`(§2 원칙, §3.3 보고 형식, §13 DoD)
- 결정 현황: `docs/decisions/open-decisions.md`
- 환경 제약: 로컬(Windows IDE)은 단위 테스트·`mvn verify`·CI까지만. 통합·배포 검증은 소스 개발 완료 후 Linux 서버에서.
