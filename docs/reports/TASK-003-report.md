# [작업 결과] TASK-003 동시 입장 상한과 입장 lease·heartbeat (QUE-005, QUE-007)

- 작성일: 2026-11-14
- 작성: Claude (검토·승인 필요)

## 1. 작업 ID
TASK-003 (Phase 1)

## 2. 관련 요구사항 ID
QUE-005, QUE-007(도메인 부분; Redis lease 는 Phase 2) — 상세 명세·AC: SRS §3. 회귀: QUE-001, QUE-003, QUE-004.

## 3. 구현 내용
- `QueuePolicy`에 `concurrencyLimit`(1 이상), `admissionLeaseTtl`(양수) 추가. 기본값 없음(D-QUE-004-3 유지).
- `WaitingQueue`
  - 입장 집합(`admitted`, 입장 순서 유지) 추가. ADMITTED 세션만 포함한다.
  - `admit(requestId, now)`: 남은 슬롯만큼 선정. `admit(requestId, requestedSlots, now)`: `min(요청, 남은 슬롯)`로 상한 제한(D-QUE-005-1).
  - 입장 시 `last_seen_at` 을 입장 시각으로 갱신해 lease 를 시작(D-QUE-007-1). heartbeat 가 lease 를 연장한다.
  - `expireLeases(now)`: `last_seen_at + lease_ttl <= now` 인 ADMITTED → EXPIRED, 슬롯 회수. `admit` 은 `expireLeases` → `expire` 를 선행 호출.
  - `availableSlots(now)`: 상태 변경 없이 lease 유효 세션만 점유로 계산(D-QUE-005-2).
  - `cancel` 이 ADMITTED 를 입장 집합에서 제거해 슬롯 즉시 회수(AC-QUE-005-4).
  - `expire`/`expireLeases` 공통 로직을 `expireFrom` 으로 추출. 문자열 리터럴 `"waiting_session_id"`, `"now"` 상수화(TASK-002 §10 이월 정리).
- `WaitingSessionStatus` 상태 전이 Javadoc 에 `ADMITTED → EXPIRED`(lease 만료) 추가.

## 4. 변경 파일
- main: `QueuePolicy.java`, `WaitingQueue.java`, `WaitingSessionStatus.java`(주석)
- test: 신규 `AdmissionCapacityTest.java`(15건), `WaitingQueueTest.java`·`WaitingSessionLifecycleTest.java`(정책 생성자 인자 추가만, 검증 내용 불변)
- docs: `SRS.md`(QUE-005/007 상세, §2.2 상태, D-QUE-005-1~3, D-QUE-007-1~4), `traceability-matrix.md`, `PROGRESS.md`, `CHANGELOG.md`, 증거 로그 2건

## 5. 설계 변경 여부
상위 설계(설계서 7.3) 변경 없음. 제안 결정 D-QUE-005-1~3, D-QUE-007-1~4 승인 필요.
공개 API 변경: `QueuePolicy` 생성자 인자 2개 추가, `WaitingQueue` 에 `admit(requestId, now)`·`expireLeases`·`availableSlots` 추가.
기존 `admit(requestId, slots, now)` 의 의미가 "요청 인원"으로 바뀌어 남은 슬롯으로 제한된다(이 모듈만 사용, I1).

## 6. 영향도 분석
- 이전 단계: QUE-001(16), QUE-003(7), QUE-004(9) 테스트 전부 통과. 테스트는 정책 생성자 인자만 추가했고 단언은 바꾸지 않았다.
  기존 테스트는 상한 10 으로 설정해 QUE-001 의 경계값(5명 요청) 검증이 그대로 유효하다. D-QUE-004-5(`expire` 는 WAITING 만) 유지.
- 현재 단계: "lease 유효 ADMITTED 수 ≤ 상한" 불변조건이 모든 `admit` 경로에 적용된다. `availableSlots` 는 O(입장 수) 순회.
  lease 만료 상태가 `EXPIRED` 로 합쳐져 "대기 만료"와 "입장 만료"를 상태만으로는 구분할 수 없다(D-QUE-007-2, 아래 §12).
- 다음 단계: TASK-004(QUE-006 입장률)는 `admit(requestId, requestedSlots, now)` 의 `requestedSlots` 로 결합하면 된다.
  Phase 2 Redis 는 대기 ZSET 과 입장(lease) ZSET 두 개를 `last_seen_at` score 로 두면 이 모델과 1:1 대응한다.
  Phase 3 Waiting Room 이 lease 만료 시각을 표시하려면 `last_seen_at + policy.admissionLeaseTtl` 로 계산한다.
- 영향도 등급: I1.

## 7. 작성/수정한 테스트
| 테스트 | 건수 | AC |
|---|---|---|
| `AdmissionCapacityTest.ConcurrencyLimit` | 7 | AC-QUE-005-1~6 + null 검증 |
| `AdmissionCapacityTest.AdmissionLease` | 8 | AC-QUE-007-1~8 |
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
| Red | 컴파일 실패(새 생성자·`admit(id, now)`·`availableSlots`·`expireLeases` 미구현) → BUILD FAILURE | `docs/test/evidence/TASK-003-red.log` |
| Green | **Tests run: 65, Failures: 0, Errors: 0, Skipped: 0** → BUILD SUCCESS (1차 통과, 테스트 수정 없음) | `docs/test/evidence/TASK-003-green.log` |

통합·E2E 테스트: 해당 없음(도메인 단위). Redis lease 원자성은 Phase 2 QUE-008 에서 서버 환경 통합 테스트로 검증 예정(미실행).

## 10. 정적 분석 결과
Checkstyle 0건, SpotBugs 0건(BugInstance size 0), enforcer 통과, `-Xlint:all -Werror` 경고 0건 (clean verify 성공).

## 11. 보안 검토 결과
- 외부 연결·비밀정보·로그 없음. 오류 메시지에 입력 원문 미포함 규칙 유지.
- 가용성: 호출자가 넘기는 입장 인원을 신뢰하지 않고 상한으로 다시 제한하므로, 상위 계층 버그나 중복 호출로 원본 서비스가 상한을 넘어 과부하되는 경로를 도메인에서 차단한다.
- lease TTL 상·하한 검증(너무 길면 좀비 점유, 너무 짧으면 정상 사용자 단절)은 Control Plane 정책 검증(Phase 5) 과제로 명세에 기록.
- 입장 토큰(TOK) 수명과 lease 는 독립(D-QUE-007-4). 토큰 계약은 Phase 6 승인 사항.

## 12. 미해결 사항
- D-QUE-005-1~3, D-QUE-007-1~4 승인 필요. 특히 **D-QUE-007-2**: lease 만료를 `EXPIRED` 로 합칠지, 사용자 안내 구분을 위해 `LEASE_EXPIRED` 를 분리할지 소유자 결정 요청(Phase 3 전까지, 지금 바꾸면 비용 작음).
- CI 는 이 커밋 푸시 후 실행(결과는 다음 세션에서 확인).
- Phase 1 잔여: QUE-006 입장률 제한 (TASK-004).

## 13. 다음 작업 제안
TASK-004 (QUE-006 초당/분당 입장률 제한) — `docs/PROGRESS.md` §2 참조. 완료 시 Phase 1 종료 보고 후 Phase 2 착수 전 Redis key 설계 결정.
