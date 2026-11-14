# 요구사항-테스트 추적표

- 갱신: 2026-11-14 (TASK-004, Phase 1 완료)
- 명세 상태: 초안 / 검토중 / 승인 · 검증 상태: 미착수 / 진행중 / 통과 / 실패
- **코드가 존재한다는 사실만으로 요구사항이 구현된 것으로 간주하지 않는다.** 검증 상태 `통과`는 테스트 증거가 있을 때만 쓴다.

| 요구사항 ID | 명세 상태 | 설계 문서 | 코드 모듈 / 클래스 | 테스트 ID / 경로 | 검증 상태 | 증거 |
|---|---|---|---|---|---|---|
| QUE-001 | 검토중 | SRS §3 QUE-001 | `openqueue-queue-domain` / `WaitingQueue`, `AdmissionResult`, `RegistrationResult`, `WaitingSessionStatus` | AC-QUE-001-1~5 / `WaitingQueueTest` (16건) | 통과 (단위) | [red](evidence/TASK-000-red.log), [green](evidence/TASK-000-green.log) |
| QUE-001 | 검토중 | SRS §3 D-QUE-001-3 | `openqueue-queue-domain` / `SiteId`, `QueueId`, `QueueScope`, `WaitingSessionId`, `AdmissionRequestId`, `IdentifierRules` | AC-QUE-001-6 / `IdentifierTest` (18건) | 통과 (단위) | 동일 |
| QUE-001 | 검토중 | - | - | 동시성·원자성 (QUE-008, Redis) | 미착수 | - |
| SEC-001 | 초안 | api/error-and-logging-conventions.md | `IdentifierRules`, `WaitingSessionId.toString` (부분) | `IdentifierTest.errorMessageDoesNotEchoInput`, `sessionIdToStringIsMasked` | 부분 통과 (도메인 식별자 한정) | 동일 |
| NET-001 | 초안 | ADR-0003, zone-security-policy.md | `openqueue-queue-domain/pom.xml` enforcer | 빌드 경계 반대 케이스 (TASK-000 보고서 §9) | 부분 통과 (빌드 규칙만, 네트워크 검증 미착수) | TASK-000 보고서 |
| NET-002~006 | 초안 | zone-security-policy.md, port-matrix.md | - | - | 미착수 | - |
| QUE-002 | 검토중 | SRS D-QUE-001-3, DEC-12 | `SiteId`, `QueueId`, `QueueScope` | `IdentifierTest` | 통과 (단위) | TASK-000 증거 |
| QUE-003 | 검토중 | SRS §3 QUE-003 | `WaitingQueue.register/find/cancel`, `WaitingSession` | AC-QUE-003-1~6 / `WaitingSessionLifecycleTest.CreateLookupCancel` (7건) | 통과 (단위) | [red](evidence/TASK-002-red.log), [green](evidence/TASK-002-green.log) |
| QUE-004 | 검토중 | SRS §3 QUE-004 | `QueuePolicy`, `ReentryPolicy`, `WaitingQueue.heartbeat/expire/admit` | AC-QUE-004-1~8 / `WaitingSessionLifecycleTest.ExpiryAndReconnect` (9건) | 통과 (단위) | 동일 |
| QUE-005 | 검토중 | SRS §3 QUE-005 | `QueuePolicy.concurrencyLimit`, `WaitingQueue.availableSlots/admit(requestId, now)` | AC-QUE-005-1~6 / `AdmissionCapacityTest.ConcurrencyLimit` (7건) | 통과 (단위) | [red](evidence/TASK-003-red.log), [green](evidence/TASK-003-green.log) |
| QUE-007 | 검토중 (도메인 부분) | SRS §3 QUE-007 | `QueuePolicy.admissionLeaseTtl`, `WaitingQueue.expireLeases/heartbeat/cancel` | AC-QUE-007-1~8 / `AdmissionCapacityTest.AdmissionLease` (8건) | 통과 (단위) · Redis lease 는 Phase 2 미착수 | 동일 |
| QUE-006 | 검토중 | SRS §3 QUE-006 | `AdmissionRateLimit`(Unlimited/PerWindow), `QueuePolicy.admissionRate`, `WaitingQueue.admit`(창 집계) | AC-QUE-006-1~9 / `AdmissionRateLimitTest` (9건) | 통과 (단위) | [red](evidence/TASK-004-red.log), [green](evidence/TASK-004-green.log) |
| QUE-008, QUE-009 | 초안 | SRS §2.2 | - | - | 미착수 (Phase 2) | - |
| SEC-002, GAT-*, TOK-*, POL-*, ADM-*, WUI-*, DB-*, OBS-*, OPS-*, NFR-* | 초안 | SRS §2.3 | - | - | 미착수 | - |
