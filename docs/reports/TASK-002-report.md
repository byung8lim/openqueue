# [작업 결과] TASK-002 대기 세션 생성·조회·취소, 만료·재접속 (QUE-003, QUE-004)

- 작성일: 2026-11-14
- 작성: Claude (검토·승인 필요)

## 1. 작업 ID
TASK-002 (Phase 1)

## 2. 관련 요구사항 ID
QUE-003, QUE-004 (상세 명세·AC: SRS §3), QUE-001 회귀, QUE-002(DEC-12 확정 반영)

## 3. 구현 내용
- `WaitingSessionStatus`에 `EXPIRED`, `CANCELLED` 추가(종단 상태), 상태 전이 Javadoc.
- `QueuePolicy(waitingSessionTtl, reentry)` + `ReentryPolicy(ALLOW/DENY)` — 기본값 없음(D-QUE-004-3).
- `WaitingQueue`: `register(id, now)`(재접속·재진입 처리), `heartbeat`, `cancel`, `expire(now)`, `admit(requestId, slots, now)`(선정 전 만료 처리), `find`.
- `RegistrationResult`에 `RegistrationOutcome`(REGISTERED / ALREADY_WAITING / ALREADY_ADMITTED / REENTRY_DENIED) 추가, `newlyRegistered()` 유지.
- `WaitingSession` 조회 뷰(상태·등록 시각·마지막 활동 시각).

## 4. 변경 파일
- main: `WaitingQueue.java`(재작성), `WaitingSessionStatus.java`, `RegistrationResult.java`, 신규 `QueuePolicy.java`, `ReentryPolicy.java`, `RegistrationOutcome.java`, `WaitingSession.java`
- test: 신규 `WaitingSessionLifecycleTest.java`(16건), `WaitingQueueTest.java`(시그니처 갱신, 검증 내용 불변)
- docs: `SRS.md`(QUE-003/004 상세, D-QUE-004-1~5), `traceability-matrix.md`, `PROGRESS.md`, `CHANGELOG.md`, 증거 로그

## 5. 설계 변경 여부
상위 설계 변경 없음. 제안 결정 D-QUE-004-1~5 (SRS) 승인 필요. 공개 API 변경: `WaitingQueue` 생성자·`register`·`admit`에 인자 추가(I1, 이 모듈만 사용).

## 6. 영향도 분석
- 이전 단계: QUE-001 테스트 18건 전부 통과(시그니처만 갱신). 멱등·FIFO 규칙 불변.
- 현재 단계: 종단 상태 세션을 메모리에 보관(D-QUE-004-4) → 장기 실행 시 메모리 증가. Phase 2 Redis TTL로 해결 전제.
- 다음 단계: TASK-003(lease)은 `ADMITTED` 세션의 만료를 추가해야 하므로 `expire`가 WAITING만 다루는 현재 경계(D-QUE-004-5)를 확장. `QueuePolicy`에 필드 추가 예정.
- 영향도 등급: I1.

## 7. 작성/수정한 테스트
| 테스트 | 건수 | AC |
|---|---|---|
| `WaitingSessionLifecycleTest.CreateLookupCancel` | 7 | AC-QUE-003-1~6 + null 검증 |
| `WaitingSessionLifecycleTest.ExpiryAndReconnect` | 9 | AC-QUE-004-1~8 |
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
| Red | 컴파일 실패(새 시그니처·타입 미구현) → BUILD FAILURE | `docs/test/evidence/TASK-002-red.log` |
| Green 1차 | 50건 중 2건 실패 — **테스트 설정 오류**(B를 T0에 등록해 expire 가 B까지 만료시킨 뒤 cancel 시도). 취소를 만료 전에 수행하도록 테스트 순서 수정. 구현 변경 없음 | 콘솔 |
| Green 최종 | **Tests run: 50, Failures: 0, Errors: 0** → BUILD SUCCESS | `docs/test/evidence/TASK-002-green.log` |

## 10. 정적 분석 결과
Checkstyle 0건, SpotBugs 0건, enforcer 통과, `-Xlint:all -Werror` 경고 0건 (clean verify 성공).
IDE(Sonar) 힌트: 문자열 리터럴 `"waiting_session_id"` 3회 중복 — 상수화는 TASK-003에서 정리 예정.

## 11. 보안 검토 결과
- 외부 연결·비밀정보 없음. 오류 메시지에 입력 원문 미포함 규칙 유지.
- 취소·heartbeat 는 세션 ID 소지가 곧 권한이므로 IDOR 방어는 API 계층(Phase 3) 과제로 명세에 기록.

## 12. 미해결 사항
- D-QUE-004-1~5 승인 필요. CI는 이 커밋 푸시 후 실행(결과는 다음 세션에서 확인).
- Phase 1 잔여: QUE-005, QUE-006, QUE-007 (`docs/PROGRESS.md` §2).

## 13. 다음 작업 제안
TASK-003 (QUE-005 + QUE-007) — `docs/PROGRESS.md` 참조.
