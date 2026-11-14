# [작업 결과] TASK-000 개발 기반 구축

- 작성일: 2026-11-14
- 작성: Claude (검토·승인 필요)

## 1. 작업 ID
TASK-000 (개발요청서 §17)

## 2. 관련 요구사항 ID
QUE-001(샘플 TDD 기능), QUE-002(식별자, 최소 범위), SEC-001(부분), NET-001(빌드 경계 부분)

## 3. 구현 내용

| 작업 범위(§17) | 결과 |
|---|---|
| 1. 기존 저장소 조사 | 기존 코드 없음. `docs/` 문서 2건만 존재 확인 |
| 2. 권장 저장소 구조 생성 | §6 구조 생성 (`backend/`, `frontend/`, `database/`, `deployment/<zone>`, `tests/`, `docs/*`), git 초기화(`main`, 커밋 없음) |
| 3. 빌드 도구 확정 | Maven 멀티모듈 + Java 21 대상, Frontend는 npm+Vite (ADR-0001) |
| 4. 요구사항 ID·문서 템플릿 | `docs/requirements/SRS.md`(초기 ID 40여 건), `templates/requirement-template.md` |
| 5. ADR 템플릿 | `ADR-0000-template.md` + ADR-0001~0003 |
| 6. 단위·통합 테스트 환경 | JUnit 5 + AssertJ, surefire(`*Test`)/failsafe(`*IT`) 분리 규칙. 통합 테스트 실체는 Phase 2 |
| 7. 정적 분석·CI | Checkstyle, SpotBugs, JaCoCo, enforcer(`mvn verify`), GitHub Actions(`backend-verify`, `secret-scan`), PR 템플릿, 로컬 동일 게이트 `scripts/verify.sh` |
| 8. 한국어 주석·SQL 분리 규칙 | CONTRIBUTING.md §3, migration-policy.md, Checkstyle 공개 타입 Javadoc 필수 |
| 9. 네트워크 Zone·모듈 경계 문서 | zone-security-policy.md, port-matrix.md, module-design.md, architecture.md |
| 10. 샘플 도메인 기능 TDD | QUE-001 FIFO 입장 후보 선정 + 재전송 멱등성 (`openqueue-queue-domain`) |

## 4. 변경 파일
모두 신규. 주요 파일:
- 빌드: `backend/pom.xml`, `backend/openqueue-queue-domain/pom.xml`, `backend/config/**`, `backend/.mvn/*`
- 코드: `backend/openqueue-queue-domain/src/main/java/com/wbl/openqueue/queue/domain/*.java` (10개, OD-02 반영)
- 테스트: `WaitingQueueTest.java`, `IdentifierTest.java`
- 저장소: `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md`, `CLAUDE.md`, `.gitignore`, `.editorconfig`, `.gitattributes`, `.github/**`
- 문서: `docs/requirements`, `docs/adr`, `docs/architecture`, `docs/network`, `docs/api`, `docs/database`, `docs/test`, `docs/decisions`, `docs/operations`(placeholder)

## 5. 설계 변경 여부
상위 설계서 변경 없음. 설계서에 없던 세부 결정은 모두 **제안 상태**로 기록함:
D-QUE-001-1~3 (SRS), ADR-0001~0003, OD-01~09 (open-decisions.md).

## 6. 영향도 분석
- 이전 단계: 해당 없음(최초 작업).
- 현재 단계: 샘플 도메인 모듈만 생성. 외부 연결(DB/Redis/네트워크) 없음.
- 다음 단계:
  - Phase 1: `WaitingQueue`의 상태(`WAITING`, `ADMITTED`)에 만료·취소 상태 추가 시 상태 전이 테스트 확장 필요.
  - Phase 2: 식별자 형식(D-QUE-001-3)이 Redis key namespace의 전제가 됨 → 형식 변경 시 I3.
    `WaitingQueue`는 스레드 안전하지 않으므로 Redis Lua 구현이 동일 규칙(FIFO·멱등)을 원자적으로 재현해야 하며,
    이 테스트 케이스를 Redis Adapter 통합 테스트의 기준 시나리오로 재사용할 것을 권장.
  - Phase 6: `QueueScope`가 토큰 `site_id`/`queue_id` claim의 원천이 됨.
- 영향도 등급: I1 (신규 모듈, 공개 계약 없음). 단 ADR-0003은 보안 경계 결정이므로 승인 시 I4 절차 적용.

## 7. 작성/수정한 테스트
| 테스트 클래스 | 건수 | 대응 AC |
|---|---|---|
| `WaitingQueueTest` | 16 | AC-QUE-001-1 ~ 5 (+ 결과 불변성, 범위 노출) |
| `IdentifierTest` | 18 | AC-QUE-001-6, SEC-001(오류 메시지 입력 미반영, 세션 ID 마스킹) |

## 8. 실행한 테스트 명령
```bash
cd backend
mvn test                     # Red 단계 (골격 구현)
mvn test                     # Green 단계
mvn -B -ntp clean verify     # 최종 회귀 (enforcer + 테스트 + Checkstyle + SpotBugs + JaCoCo)
# 경계 규칙 반대 케이스: 스크래치 복사본의 도메인 POM에 compile 의존성 주입 후
mvn -B validate
```

## 9. 실제 테스트 결과
| 단계 | 결과 | 증거 |
|---|---|---|
| Red | Tests run: 34, Failures: 15, Errors: 15 → BUILD FAILURE (의도된 실패) | `docs/test/evidence/TASK-000-red.log` |
| Green | Tests run: 34, Failures: 0, Errors: 0 → BUILD SUCCESS | `docs/test/evidence/TASK-000-green.log` |
| 최종 `clean verify` | 34/34 통과, BUILD SUCCESS (11.5s) | 동일 |
| 커버리지 (JaCoCo) | 라인 79/79 (100%), 분기 24/24 (100%) | `target/site/jacoco` |
| 경계 규칙 반대 케이스 | `junit-platform-commons`를 compile 의존성으로 주입 → `BannedDependencies failed` 메시지와 함께 BUILD FAILURE (의도대로 차단) | 본 보고서 |

**실행 환경 주의**: 로컬은 JDK 25(`--release 21`로 컴파일)에서 실행함. PATH에 있는 `C:\dev\openjdk-21`은
실제로 존재하지 않아 **JDK 21 런타임 실행은 로컬에서 검증하지 못함** → CI(Temurin 21)에서 최초 검증 필요.

## 10. 정적 분석 결과
- Checkstyle: 위반 0건 (main + test)
- SpotBugs (effort=Max, threshold=Low): BugInstance 0건
- javac `-Xlint:all -Werror`: 경고 0건
- enforcer: 빌드 환경·의존성 수렴·도메인 순수성 규칙 모두 통과

## 11. 보안 검토 결과
- 외부 연결·비밀정보 없음. DB/Redis/네트워크 코드 없음.
- 식별자 화이트리스트 검증으로 path traversal(`shop/../admin`), 개행 삽입(`shop\nlog`) 입력 거부를 테스트로 확인.
- 오류 메시지에 입력 원문 미포함, `WaitingSessionId.toString()` 마스킹을 테스트로 고정.
- `.gitignore`에 `.env`, 키·인증서 파일 패턴 제외 설정.
- 간이 비밀정보 패턴 grep: 탐지 0건. **gitleaks는 로컬 미설치로 미실행** (CI `secret-scan` 잡에서 실행 예정, 아직 CI 미실행).
- 의존성 취약점 검사: **미실행** (OD-05). 현재 런타임 의존성 0건(테스트 의존성만 존재)이라 잔여 위험 낮음.

## 12. 미해결 사항
1. **CI 미실행**: 원격 저장소가 없어 GitHub Actions 워크플로는 작성만 되었고 실행되지 않음 (OD-03). → "CI에서 빌드·단위 테스트·정적 분석이 실행된다" 수용 기준은 **미검증**.
2. Docker 미설치 → Testcontainers 통합 테스트 환경은 규칙만 정의, 실행 불가 (Phase 2 착수 전 필요).
3. 승인 필요: SRS QUE-001 및 D-QUE-001-1~3, ADR-0001~0003, 포트 매트릭스 제안값.
4. 미결정: 라이선스(OD-01), groupId(OD-02), CI 플랫폼(OD-03), Action SHA 고정(OD-04), 취약점 검사 도구(OD-05) 외 — `open-decisions.md`.
5. 설계서 §16의 보안 의사결정(DEC-02 정책 배포 방식, DEC-03 토큰 서명 위치, DEC-09 Fail 정책)은 설계서 §15 권고대로 Phase 0에서 확정 필요.
6. Frontend 스캐폴딩은 Phase 3으로 연기(빌드 도구만 확정).

## 12-1. 후속 반영 (2026-11-14, 소유자 결정 수신 후)

- §12-4의 OD-01/02/03/04/06/09 결정, DEC-01~04·06~12 결정을 반영했다. 현황은 `docs/decisions/open-decisions.md` §1·§4.
- 패키지 `com.wbl.openqueue` 전환 후 `scripts/verify.sh`(= `mvn clean verify`) 34/34 통과 재확인.
- 3차 결정: DEC-02/06 → Distribution API(ADR-0009, Redis 경유 ADR-0008 기각), DEC-03·OD-05 권고안 채택, DEC-07 `initpassword.txt` 방식(ADR-0006).
- CI Action SHA 고정(OD-04)·Dependabot 설정은 작성만 되었고, 최초 푸시 후 CI 실행 결과는 TASK-001에서 보고한다.
- 남은 확인: 내장 Admin 계정 MFA 여부(Phase 5 전), 기존 GW/LB의 Ed25519 지원(Phase 6 전), A6/A10 보안·네트워크팀 검토.

## 13. 다음 작업 제안
- **TASK-001 (Phase 0 마무리)**: 원격 저장소 생성 후 CI 최초 실행·통과 확인, 위 승인 항목 검토, DEC-02/03/09 결정 회의.
- **TASK-002 (Phase 1)**: QUE-003 대기 세션 조회·취소 + QUE-004 만료/재접속 규칙 (Clock 주입), 상세 명세 작성·승인 후 착수.
- **TASK-003 (Phase 1)**: QUE-005 동시 입장 상한 기반 입장 가능 인원 계산.
