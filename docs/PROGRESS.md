# 진행 현황 · 재개 지점 (PROGRESS)

> 작업을 중단·재개할 때 가장 먼저 읽는 문서. 매 작업 종료 시 갱신한다.

- 마지막 갱신: **2026-11-14 16:05** (오후 작업, TASK-004 종료 · **Phase 1 완료**)
- 현재 Phase: **Phase 1 완료 → Phase 2 (Redis Queue Adapter) 착수 전 결정 단계**
- 마지막 완료 작업: **TASK-004** (QUE-006 입장률 제한) — 단위 테스트 74/74, `clean verify` 통과
- 저장소: https://github.com/byung8lim/openqueue (`main`, CI 통과 확인: TASK-001. TASK-002~004 커밋의 CI 결과는 GitHub Actions 에서 확인 필요)

## 0. 전체 진행률: **약 16 %**

산정 방식: Phase별 작업 항목 수(개발요청서 §9)를 예상 난이도로 가중하고, 완료 항목 비율을 곱해 합산한다.
매 TASK 종료 보고에 이 수치를 갱신한다. (가중치는 추정값이며 Phase 2 착수 시 재조정)

| Phase | 가중치 | 항목 수 | 완료 | 기여 |
|---|---|---|---|---|
| 0 기반·설계 | 8 % | 9 | 9 | 8.0 |
| 1 Queue Domain | 8 % | 7 | 7 | 8.0 |
| 2 Redis Adapter | 10 % | 6 | 0 | 0 |
| 3 Waiting Room·API | 14 % | 5 | 0 | 0 |
| 4 Gateway·라우팅 | 14 % | 7 | 0 | 0 |
| 5 Control Plane·Admin | 16 % | 6 | 0 | 0 |
| 6 정책 배포·토큰 | 12 % | 7 | 0 | 0 |
| 7 HA·운영 | 10 % | 8 | 0 | 0 |
| 8 보안 검증·릴리스 | 8 % | 8 | 0 | 0 |
| **합계** | 100 % | 63 | 16 | **16.0** |

## 1. Phase 진행표

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 개발 기반·설계 확정 | **완료** (TASK-000, TASK-001) |
| 1 | Queue Domain Core | **완료** (TASK-000 샘플, TASK-002~004) — QUE-001~007 단위 테스트 74건, 명세 상태는 `검토중`(소유자 승인 대기) |
| 2 | Redis Queue Adapter | **착수 전 결정 대기** (§2). Docker 없음 → 통합 테스트는 CI 또는 서버 환경 |
| 3~8 | Waiting Room API / Gateway / Control Plane / 정책·토큰 / HA / 보안 검증 | 미착수 |

## 2. 다음에 할 일 (순서대로)

1. **소유자 승인 요청 (Phase 1 종료)**: SRS §3 의 제안 결정 D-QUE-001-1~3, D-QUE-004-1~5, D-QUE-005-1~3, D-QUE-006-1~5, D-QUE-007-1~4.
   승인되면 QUE-001~007 상태 `검토중` → `승인`, 추적표 갱신. 특히 확인 요청:
   - D-QUE-007-2: lease 만료를 `EXPIRED` 로 합칠지 `LEASE_EXPIRED` 로 분리할지
   - D-QUE-006-2: 취소·lease 만료가 입장률 집계를 되돌리지 않는 것이 운영 의도와 맞는지
2. **TASK-005 (Phase 2 설계)**: `docs/database/redis-keys.md` — key namespace(`oq:{site}:{queue}:...` hash tag), 자료구조, 값 schema, TTL.
   후보: 대기 ZSET(score=last_seen_at), 입장 lease ZSET(score=last_seen_at), 입장 사건 ZSET(score=입장 시각) 또는 고정 창 카운터,
   세션 해시(상태·registered_at), 멱등 키(결과, TTL). Redis 계약 변경은 I3 → 소유자 승인 필요.
3. **TASK-006 (Phase 2 설계)**: Lua 스크립트 계약 문서 — register / heartbeat / cancel / admit(회수+상한+입장률+멱등) 의 입력·출력·오류 코드·원자성.
4. **TASK-007~**: `openqueue-queue-infrastructure` 모듈(Lettuce, TLS/ACL 설정), 포트 인터페이스(domain ↔ adapter), Testcontainers 통합 테스트
   (로컬 실행 불가 → CI 잡 추가 또는 서버 환경에서 실행하는 방침 결정 필요).

## 3. 열린 확인 사항 (막히지 않음, 답 오면 반영)

- D-QUE-007-2, D-QUE-006-2 — 위 §2-1
- Phase 2 통합 테스트 실행 위치(CI Docker 서비스 vs Linux 서버) — Phase 2 구현 전
- 내장 Admin 계정 MFA(TOTP) 요구 여부 — Phase 5 전 (ADR-0006)
- 기존 API Gateway/LB의 Ed25519 지원 여부 — Phase 6 전 (ADR-0005)
- A6/A10 DMZ → 관리망 통신 보안·네트워크팀 검토 (ADR-0009, ADR-0005)
- Dependabot PR 9건 처리 방침 (TASK-001 보고서 §12) — 메이저(JUnit 6, Action 메이저)는 로컬 검증 후 병합 권고
- GitHub 설정: `main` 브랜치 보호, Private vulnerability reporting 활성화 (사람이 수행)

## 4. 재개 절차

```bash
git pull
bash scripts/verify.sh          # 74/74, BUILD SUCCESS 확인
# 그 다음 §2-1 승인 여부 확인 → TASK-005 Redis key 설계 문서 작성(코드 변경 없음, I0)
```

- 개발 규칙: `CLAUDE.md`, `docs/claude.md`(§2 원칙, §3.3 보고 형식, §13 DoD)
- 결정 현황: `docs/decisions/open-decisions.md`
- 환경 제약: 로컬(Windows IDE)은 단위 테스트·`mvn verify`·CI까지만. 통합·배포 검증은 소스 개발 완료 후 Linux 서버에서.
