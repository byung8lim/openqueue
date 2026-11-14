# ADR-0002: 테스트 및 정적 분석 표준 조합

- 상태: 제안 (TASK-000에서 작성, 승인 필요)
- 작성일: 2026-11-14
- 관련: 개발요청서 §8.2 ("도구는 중복 도입을 피하고 초기 단계에서 표준 조합을 확정한다")
- 영향도 등급: I1

## 결정

| 대상 | 도구 | 버전(고정) | 적용 시점 |
|---|---|---|---|
| 단위 테스트 | JUnit Jupiter | 5.13.4 | Phase 0 |
| Assertion | AssertJ | 3.27.6 | Phase 0 |
| Mock | Mockito | Phase 1 application 계층 도입 시 고정 | Phase 1 |
| 통합 테스트 | Spring Boot Test + Testcontainers (`*IT`, failsafe) | 모듈 도입 시 고정 | Phase 2 |
| API 테스트 | MockMvc / WebTestClient | 모듈 도입 시 고정 | Phase 3 |
| 코드 스타일·가독성 | Checkstyle | 10.26.1 (plugin 3.6.0) | Phase 0 |
| 결함 패턴 | SpotBugs | plugin 4.9.8.2, effort=Max, threshold=Low | Phase 0 |
| 커버리지 | JaCoCo | 0.8.14 | Phase 0 (리포트만, 임계치는 OD-07) |
| 모듈 경계 | maven-enforcer bannedDependencies | 3.6.2 | Phase 0 |
| 비밀정보 탐지 | gitleaks (CI) | action v2 | Phase 0 |
| 의존성 취약점 | OWASP Dependency-Check | 미정 | NVD API 키 확보 후 (OD-05) |
| Frontend | Vitest, React Testing Library, Playwright | Phase 3 | Phase 3 |

Spotless(포매터)는 Checkstyle과 역할이 겹치므로 도입하지 않는다. 포맷은 `.editorconfig`로 맞춘다.

## Checkstyle 규칙 방향

스타일 취향이 아닌 **가독성·결함 위험** 항목 위주: 메서드 60줄, 순환 복잡도 10, if 중첩 2, 와일드카드 import 금지,
빈 catch 금지, `Throwable`/`Error` catch 금지, 공개 타입 Javadoc 필수. 위반 시 빌드 실패.

## 결과와 영향

- `mvn verify` 하나로 로컬과 CI가 동일한 게이트를 실행한다.
- 규칙 완화(예외 추가)는 `config/` 파일 변경으로 드러나며, 사유를 주석으로 남겨야 한다.
