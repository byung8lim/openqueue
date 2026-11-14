# OpenQueue 기여 가이드

이 문서는 개발요청서([docs/claude.md](docs/claude.md))의 개발 원칙을 실무 규칙으로 정리한 것이다.
사람 개발자와 Claude 모두 동일하게 적용한다.

## 1. 작업 시작 전

1. 관련 요구사항 ID와 승인된 명세를 확인한다 ([SRS](docs/requirements/SRS.md)).
2. 명세가 없거나 모호하면 코드를 쓰기 전에 질문하고, 결정 사항을 SRS의 "결정 사항" 표에 기록한다.
3. 변경 대상·의존 모듈·이전/현재/다음 단계 영향도와 영향도 등급(I0~I4)을 정리한다.
4. 보안 경계(Zone, 인증·인가, 키, 스키마 계약, Fail-open/closed)에 영향이 있으면 **사람의 승인 없이 진행하지 않는다**.

## 2. TDD 절차 (필수)

1. 수용 기준(AC) 확정 → 2. 테스트 작성 → 3. 실패 확인(Red) → 4. 최소 구현 → 5. 통과(Green)
→ 6. 리팩터링 → 7. 관련 테스트 전체 재실행 → 8. `mvn verify`(정적 분석 포함) → 9. 리뷰 → 10. 추적표 갱신

- 테스트를 통과시키기 위해 테스트를 삭제·`@Disabled` 처리하거나 검증을 약화하지 않는다.
- 테스트 클래스/메서드에는 대응하는 AC ID를 `@DisplayName`에 명시한다. (예: `AC-QUE-001-1 ...`)
- 테스트 이름 규칙: 단위 테스트 `*Test`, 통합 테스트 `*IT` (surefire / failsafe 분리).

## 3. 코딩 규칙

### 3.1 계층과 모듈

- Controller에 업무 규칙을 두지 않는다. 업무 규칙은 domain, 유스케이스는 application 계층에 둔다.
- DB·Redis·외부 시스템 연계는 infrastructure 모듈의 Adapter/Repository로 분리한다.
- 모듈 의존 방향은 [module-design.md](docs/architecture/module-design.md)를 따른다. 순환 의존 금지.
- 모듈 경계는 maven-enforcer 규칙으로 빌드 시 강제한다. 규칙을 완화하려면 ADR이 필요하다.

### 3.2 한국어 주석

- 주요 업무 로직, 보안 검증, 동시성 제어, 상태 전이, 예외 처리, 복잡한 알고리즘의 주석은 **한국어**로 작성한다.
- 공개 클래스와 공개 메서드에는 Javadoc으로 **목적 / 입력 / 출력 / 예외 / 주의사항**을 쓴다.
- 주석은 코드를 반복하지 말고 **왜 필요한지, 어떤 불변조건을 보호하는지**를 쓴다.
- 식별자·API 필드·표준 기술 용어는 영어를 허용한다.
- 주석과 동작이 달라지면 함께 수정한다. 모든 파일은 UTF-8.

좋은 예:

```java
// 재전송된 요청: 새로운 선정을 하지 않아야 중복 입장이 발생하지 않는다.
return previous;
```

나쁜 예:

```java
// previous를 반환한다.
return previous;
```

### 3.3 SQL 분리 규칙

- SQL을 Java 문자열로 하드코딩하지 않는다.
- DDL/DML·migration은 `database/migration/V<버전>__<설명>.sql` 파일로 관리한다 (Flyway).
- 동적 SQL은 반드시 파라미터 바인딩을 사용한다. 문자열 연결로 SQL을 만들지 않는다.
- 테이블·컬럼·인덱스 변경 시 [ERD.md](docs/database/ERD.md)와 데이터 사전을 함께 수정한다.
- 상세: [migration-policy.md](docs/database/migration-policy.md)

### 3.4 보안 규칙

- 소스·테스트·문서에 비밀정보(비밀번호, 키, 토큰)를 쓰지 않는다. `.env`·키 파일은 커밋하지 않는다.
- 로그·예외 메시지·HTTP 응답에 토큰 원문, 세션 ID 원문, 외부 입력 원문을 그대로 넣지 않는다.
- 모든 외부 입력(식별자, URL, Host, path, header)은 검증 후 사용한다.
- DMZ 모듈에 MariaDB 드라이버·DB 접속 정보를 추가하지 않는다.

## 4. 브랜치와 커밋

| 브랜치 | 용도 |
|---|---|
| `main` | 검증된 통합 브랜치. 직접 push 금지, PR + CI 통과 필수 |
| `feature/<요구사항ID>-<기능명>` | 기능 개발 (예: `feature/QUE-003-session-cancel`) |
| `fix/<요구사항ID>-<결함명>` | 결함 수정 |
| `release/<버전>` | 릴리스 안정화 |

커밋 메시지: `<type>(<요구사항ID>): <요약>` — type은 `feat`, `test`, `fix`, `docs`, `refactor`, `build`, `ci`, `chore`.

```text
feat(QUE-001): FIFO 대기열 등록 기능 구현
test(QUE-001): 대기열 순서 및 중복 등록 테스트 추가
```

한 커밋은 하나의 논리적 목적만 가진다. 테스트 없는 기능 커밋은 하지 않는다.

## 5. Pull Request

[.github/pull_request_template.md](.github/pull_request_template.md)의 모든 항목을 채운다.
I3/I4 변경은 코드 리뷰만으로 승인하지 않는다(보안 설계 승인·보안 테스트 필요).

## 6. 작업 완료 보고

작업 후 [docs/reports/](docs/reports/)에 개발요청서 §3.3 형식의 보고서를 남긴다.
**테스트를 실행하지 못했으면 통과로 표시하지 않고** 미실행 사유와 잔여 위험을 기록한다.
