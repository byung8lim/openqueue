# ADR-0003: 모듈·Zone 경계를 빌드 단계에서 강제

- 상태: 제안 (TASK-000에서 작성, 승인 필요)
- 작성일: 2026-11-14
- 관련: NET-001, NET-006, 개발요청서 §2.2, §4.2
- 영향도 등급: I4 (보안 경계 관련 결정이므로 보안팀 승인 필요)

## 배경

"DMZ에서 MariaDB 접속 금지", "순환 의존 금지", "Controller에 업무 규칙 금지" 같은 규칙은 문서만으로는 지켜지지 않는다.
같은 저장소에서 DMZ 모듈과 내부 관리망 모듈을 함께 개발하므로, 실수로 DMZ 모듈이 DB 드라이버나 Control Plane 모듈에
의존하게 되는 것을 **빌드 단계에서** 막아야 한다.

## 결정

1. 각 모듈 POM에 `maven-enforcer-plugin`의 `bannedDependencies` 규칙을 둔다.
2. 모듈 유형별 규칙:

| 모듈 유형 | 금지 의존성 | 상태 |
|---|---|---|
| `*-domain` | 모든 compile/runtime 의존성 (순수 Java) | **적용됨** (`openqueue-queue-domain`) |
| DMZ 실행 모듈 (`gateway`, `queue-application`, `queue-infrastructure`, `token` 검증부) | `org.mariadb.jdbc:*`, `mysql:*`, `com.mysql:*`, `org.flywaydb:*`, `spring-boot-starter-data-jpa`, `openqueue-control-api`, `openqueue-policy` 서명부 | Phase 2~4 모듈 생성 시 적용 |
| 내부 관리망 모듈 (`control-api`) | `openqueue-gateway`, `openqueue-queue-infrastructure` | Phase 5 적용 |

3. 규칙은 **반대 케이스 테스트**로 검증한다: 금지 의존성을 주입한 복사본에서 빌드가 실패하는지 확인한다.
   (TASK-000 검증 결과는 `docs/reports/TASK-000-report.md` 참조)
4. 코드 수준 계층 규칙(Controller → domain 직접 의존 등)은 Spring 모듈 도입 시 ArchUnit 테스트 추가를 검토한다.

## 결과와 영향

- 빌드 규칙은 네트워크 방화벽을 대체하지 않는다. 방화벽·DB 계정·Redis ACL 통제는 별도로 필수다(심층 방어).
- 규칙 완화는 ADR과 보안 승인 없이 할 수 없다.
