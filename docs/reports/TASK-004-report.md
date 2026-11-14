# [작업 결과] TASK-004 입장률 제한 (QUE-006) · Phase 1 종료

- 작성일: 2026-11-14
- 작성: Claude (검토·승인 필요)

## 1. 작업 ID
TASK-004 (Phase 1 마지막 작업)

## 2. 관련 요구사항 ID
QUE-006 — 상세 명세·AC: SRS §3. 회귀: QUE-001, QUE-003, QUE-004, QUE-005, QUE-007.

## 3. 구현 내용
- 신규 `AdmissionRateLimit`(sealed interface): `Unlimited`(제한 없음, 명시), `PerWindow(maxAdmissions, window)`(슬라이딩 창). 값 검증 포함.
- `QueuePolicy`에 `admissionRate` 추가(null 불가, 기본값 없음 — D-QUE-006-1).
- `WaitingQueue`
  - 입장 시각 기록(`recentAdmissions`, 입장 1건당 1개)을 두고 `admit` 에서 창 밖 기록 제거 → `min(요청, 남은 슬롯, 입장률 허용)` 선정 → 선정 건수 기록.
  - 재전송은 멱등 결과를 반환하므로 집계되지 않는다. 취소·lease 만료는 집계를 되돌리지 않는다(D-QUE-006-2).
  - `Unlimited` 면 기록하지 않는다(메모리 무한 증가 방지, D-QUE-006-4).

## 4. 변경 파일
- main: 신규 `AdmissionRateLimit.java`, `QueuePolicy.java`, `WaitingQueue.java`
- test: 신규 `AdmissionRateLimitTest.java`(9건), `WaitingQueueTest`·`WaitingSessionLifecycleTest`·`AdmissionCapacityTest`(정책 생성자 인자 추가만, 단언 불변)
- docs: `SRS.md`(QUE-006 상세·D-QUE-006-1~5, §2.2 상태), `traceability-matrix.md`, `PROGRESS.md`, `CHANGELOG.md`, 증거 로그 2건

## 5. 설계 변경 여부
상위 설계 변경 없음. 제안 결정 D-QUE-006-1~5 승인 필요. 공개 API: `QueuePolicy` 생성자 인자 1개 추가, 신규 타입 1개. 이 모듈만 사용(I1).

## 6. 영향도 분석
- 이전 단계: 기존 65건 테스트 전부 통과(생성자 인자만 추가). 기존 테스트는 `Unlimited` 로 두어 검증 내용이 바뀌지 않았다.
- 현재 단계: `admit` 의 선정 인원이 세 상한(요청·슬롯·입장률)의 최솟값으로 확정됐다. 입장률 집계는 세션 상태와 분리된 "사건" 기록이므로
  `availableSlots` 등 조회에 영향이 없다.
- 다음 단계(Phase 2 Redis): 대기 ZSET, 입장(lease) ZSET 에 더해 **입장 사건 ZSET(score=입장 시각)** 또는 고정 창 카운터가 필요하다.
  슬라이딩 창을 Redis 에서 정확히 구현하려면 ZSET + `ZREMRANGEBYSCORE` 가 자연스럽고, 비용이 문제면 고정 창 카운터로 근사하는 결정이 필요하다(Phase 2 착수 전 결정 항목으로 PROGRESS 에 기록).
- 영향도 등급: I1.

## 7. 작성/수정한 테스트
| 테스트 | 건수 | AC |
|---|---|---|
| `AdmissionRateLimitTest` | 9 | AC-QUE-006-1~9 |
| `AdmissionCapacityTest` (회귀) | 15 | AC-QUE-005-1~6, AC-QUE-007-1~8 |
| `WaitingSessionLifecycleTest` (회귀) | 16 | AC-QUE-003-1~6, AC-QUE-004-1~8 |
| `WaitingQueueTest` (회귀) | 16 | AC-QUE-001-1~5 |
| `IdentifierTest` (회귀) | 18 | AC-QUE-001-6 |

## 8. 실행한 테스트 명령
```bash
cd backend && mvn -B -ntp test            # Red
cd backend && mvn -B -ntp clean verify    # Green (전체 게이트)
```

## 9. 실제 테스트 결과
| 단계 | 결과 | 증거 |
|---|---|---|
| Red | 컴파일 실패 42건(`AdmissionRateLimit` 미존재, 생성자 시그니처) → BUILD FAILURE | `docs/test/evidence/TASK-004-red.log` |
| Green 1·2차 | 컴파일 실패 — **테스트 파일 치환 실수**(`T0.plus(LEASE_TTL)` 에 정책 인자가 섞여 들어감). 구현 변경 없이 테스트 표기만 원복 | 콘솔 |
| Green 최종 | **Tests run: 74, Failures: 0, Errors: 0, Skipped: 0** → BUILD SUCCESS | `docs/test/evidence/TASK-004-green.log` |

통합·E2E: 해당 없음(도메인 단위). Redis 집계 원자성은 Phase 2 서버 환경에서 검증 예정(미실행).

## 10. 정적 분석 결과
Checkstyle 0건, SpotBugs 0건(BugInstance size 0), enforcer 통과, `-Xlint:all -Werror` 경고 0건.

## 11. 보안 검토 결과
- 외부 연결·비밀정보·로그 없음. 오류 메시지에 입력 원문 미포함.
- 가용성: 입장률은 원본 서비스 순간 부하 보호 장치다. 호출자 명시 인원도 입장률로 다시 제한된다.
- 시각 신뢰: `now` 는 서버 시각만 사용한다는 전제를 Phase 3 API 명세에 기록해야 한다(SRS QUE-006 보안 고려사항).

## 12. 미해결 사항
- D-QUE-006-1~5 승인 필요. 특히 D-QUE-006-2(취소·lease 만료가 입장률을 되돌리지 않음)는 운영 관점 확인 요청.
- D-QUE-007-2(`LEASE_EXPIRED` 분리 여부) 계속 열려 있음.
- CI 결과는 푸시 후 GitHub Actions 에서 확인 필요(이 환경에 `gh` 없음).

## 13. 다음 작업 제안 — Phase 1 종료, Phase 2 준비
Phase 1 (Queue Domain Core) 7개 항목 모두 단위 테스트 증거와 함께 완료. 테스트 게이트(도메인 단위, FIFO·경계값·중복 요청·상태 전이) 통과.
Phase 2 착수 전 결정 사항:
1. 소유자 승인: D-QUE-001-1~3, D-QUE-004-1~5, D-QUE-005-1~3, D-QUE-006-1~5, D-QUE-007-1~4 (SRS §3). 승인되면 QUE-001~007 상태를 `승인` 으로 갱신.
2. Redis key 설계(`docs/database/redis-keys.md`): 대기 ZSET / 입장 lease ZSET / 입장 사건 ZSET(또는 고정 창 카운터) / 세션 해시 / 멱등 키, hash tag(Cluster 대응, QUE-008).
3. Lua 스크립트 계약(입력·출력·오류 코드): register, heartbeat, cancel, admit(만료 회수 + 상한 + 입장률 + 멱등 포함).
4. 로컬에는 Docker 가 없으므로 Phase 2 통합 테스트(Testcontainers)는 CI 또는 서버 환경에서 실행하는 방식 결정.
