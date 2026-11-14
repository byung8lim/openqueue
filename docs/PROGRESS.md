# 진행 현황 · 재개 지점 (PROGRESS)

> 작업을 중단·재개할 때 가장 먼저 읽는 문서. 매 작업 종료 시 갱신한다.

- 마지막 갱신: **2026-11-14 17:00** (TASK-005 종료 · **Phase 2 코드 완료, CI 통합 테스트 결과 확인 대기**)
- 현재 Phase: **Phase 2 → Phase 3 (Waiting Room·Queue API) 착수 가능**
- 마지막 완료 작업: **TASK-005** (QUE-008 Redis 어댑터, QUE-009 장애 정책) — 로컬 `clean verify` 통과(단위 85건), 통합 15건은 CI 에서 실행
- 저장소: https://github.com/byung8lim/openqueue (`main`). **확인 필요**: Actions 의 `redis-integration` 잡 결과(TASK-005 커밋)
- 절차: 소유자 요청으로 Phase 2 부터 **간소화 절차** 적용 — Phase 단위 작업·보고 1건, 계약 결정은 기록 후 구현(사후 승인), Red 단계 생략. 게이트(단위·정적 분석·enforcer·CI)는 유지.

## 0. 전체 진행률: **약 25 %**

산정 방식: Phase별 작업 항목 수(개발요청서 §9)를 예상 난이도로 가중하고, 완료 항목 비율을 곱해 합산한다.
매 TASK 종료 보고에 이 수치를 갱신한다. Phase 2 는 "테스트 게이트(통합) 통과" 1항목을 CI 확인 전까지 미완료로 둔다.

| Phase | 가중치 | 항목 수 | 완료 | 기여 |
|---|---|---|---|---|
| 0 기반·설계 | 8 % | 9 | 9 | 8.0 |
| 1 Queue Domain | 8 % | 7 | 7 | 8.0 |
| 2 Redis Adapter | 10 % | 7 | 6 | 8.6 |
| 3 Waiting Room·API | 14 % | 5 | 0 | 0 |
| 4 Gateway·라우팅 | 14 % | 7 | 0 | 0 |
| 5 Control Plane·Admin | 16 % | 6 | 0 | 0 |
| 6 정책 배포·토큰 | 12 % | 7 | 0 | 0 |
| 7 HA·운영 | 10 % | 8 | 0 | 0 |
| 8 보안 검증·릴리스 | 8 % | 8 | 0 | 0 |
| **합계** | 100 % | 64 | 22 | **24.6** |

Phase 2 항목: Redis TLS/ACL 연결 ✔, key namespace ✔, Lua 원자 연산 ✔, 등록·조회·삭제 ✔, TTL/lease ✔, 장애·재시도 처리 ✔, 통합 테스트 게이트 **CI 확인 대기**.

## 1. Phase 진행표

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 개발 기반·설계 확정 | **완료** (TASK-000, TASK-001) |
| 1 | Queue Domain Core | **완료** (TASK-002~004) — QUE-001~007, 단위 76건. 명세 `검토중`(승인 대기) |
| 2 | Redis Queue Adapter | **코드 완료** (TASK-005) — QUE-008/009. `RedisWaitingQueueIT` 15건은 CI `redis-integration` 잡 결과 확인 필요 |
| 3 | Waiting Room·Queue API | 착수 가능 (§2) |
| 4~8 | Gateway / Control Plane / 정책·토큰 / HA / 보안 검증 | 미착수 |

## 2. 다음에 할 일 (순서대로)

1. **CI 확인**: `redis-integration` 잡. 실패 시 Lua 스크립트·IT 수정 → 재푸시. 통과 시 추적표 QUE-008 `통과(통합)` 갱신.
2. **TASK-006 (Phase 3)**: `openqueue-queue-application` 모듈 — 유스케이스(등록/조회/heartbeat/취소/입장 주기 실행), `Clock` 주입,
   재시도·`FailurePolicy` 적용(D-QUE-008-5), 세션 ID 생성(SecureRandom 128bit+ base64url). 포트 `QueueOperations` 이동 여부 결정.
3. **TASK-007 (Phase 3)**: Queue API(Spring Boot 3.x) — 등록·상태 조회·취소·heartbeat 엔드포인트, 세션 쿠키(HttpOnly/Secure/SameSite), 입력 검증,
   오류 규약(`docs/api/error-and-logging-conventions.md`), 사용자 rate limit, SSE 상태 스트림(DEC-05 SSE 권고안 채택 확인).
4. **TASK-008 (Phase 3)**: React + Vite Waiting Room(경량, UI 라이브러리 없음), 세션 복구·만료 안내, Vitest/Playwright. Dependabot `npm` 추가.
5. 승인 묶음 요청(막히지 않음): D-QUE-001~007, D-QUE-008-1~7, 포트 위치, D-QUE-007-2(`LEASE_EXPIRED`), D-QUE-006-2.

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
bash scripts/verify.sh          # 단위 85건(domain 76 + infra 9), BUILD SUCCESS 확인
# GitHub Actions → redis-integration 잡 결과 확인 → 추적표 갱신 → TASK-006 착수
```

- 개발 규칙: `CLAUDE.md`, `docs/claude.md`(§2 원칙, §3.3 보고 형식, §13 DoD). Phase 2 부터 간소화 절차(위 머리말)
- 결정 현황: `docs/decisions/open-decisions.md`, Redis 계약: `docs/database/redis-keys.md`
- 환경 제약: 로컬(Windows IDE)은 단위 테스트·`mvn verify`까지. Docker 통합 테스트는 CI(`-Pintegration`), 배포 검증은 Linux 서버.
