# TDD 및 테스트 전략

- 문서 버전: 0.1 (TASK-000)
- 도구 결정: [ADR-0002](../adr/ADR-0002-test-and-static-analysis.md)

## 1. 테스트 계층

| 종류 | 대상 | 위치 / 명명 | 실행 | 도입 Phase |
|---|---|---|---|---|
| 단위 | domain 규칙, 입력 검증, 상태 전이, 정책·토큰 검증 로직 | `src/test/java/**/*Test.java` | `mvn test` | 0 |
| 통합 | Redis Adapter·Lua, MariaDB Repository, Spring Security, Gateway Filter | `src/test/java/**/*IT.java` (Testcontainers) | `mvn verify` (failsafe) | 2 |
| 계약 | Control API, Gateway↔Queue Engine, 정책 스냅샷 schema, 토큰 claim | `tests/contract/` | CI | 3~6 |
| 보안 | SSRF, DNS rebinding, 토큰 위조·재사용, IDOR, Redis ACL, 로그 민감정보 | 각 모듈 `*SecurityTest`/`*SecurityIT` + `tests/security/` | CI | 0~8 |
| E2E | 접속 → 대기 → 입장 → 원본 접근 | `tests/e2e/` (Playwright) | CI(야간) | 3 |
| 성능·복구 | 동시 입장 상한, failover, 정책 배포 중 재시작 | `tests/performance/` (k6 또는 Gatling) | 수동/야간 | 7 |

## 2. 작성 규칙

1. 테스트는 수용 기준(AC) ID를 `@DisplayName`에 포함한다. 추적표는 AC ID로 테스트를 찾는다.
2. 정상 / 경계값 / 오류 / 중복 요청 / 상태 전이 케이스를 모두 포함한다.
3. 보안 관련 동작(오류 메시지에 입력 미반영, 마스킹 등)은 **테스트로 고정**한다. (예: `IdentifierTest.errorMessageDoesNotEchoInput`)
4. 시간 의존 로직은 `java.time.Clock`을 주입해 테스트한다. `Thread.sleep` 기반 테스트 금지.
5. 테스트에 실제 비밀정보·운영 접속 정보를 쓰지 않는다.
6. 빌드 규칙(enforcer 등)처럼 "막혀야 하는 것"은 반대 케이스로 실제 차단을 확인한다.

## 3. 게이트

| 게이트 | 명령 | 현재 |
|---|---|---|
| 빌드 + 단위 테스트 | `mvn verify` | 적용 |
| 모듈 경계 | enforcer (`mvn verify` 포함) | 적용 |
| Checkstyle / SpotBugs | `mvn verify` 포함 | 적용 (위반 시 실패) |
| 커버리지 | JaCoCo 리포트 | 리포트만 (임계치 OD-07) |
| 비밀정보 탐지 | CI gitleaks | 적용 (CI 전용) |
| 통합 / 계약 / migration / 의존성 취약점 | - | Phase 2 이후 |

## 4. 테스트 증거

작업별 Red/Green 실행 로그를 `docs/test/evidence/<작업ID>-{red,green}.log`에 남긴다.
