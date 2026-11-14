# [작업 결과] TASK-005 Phase 2 — Redis Queue Adapter (QUE-008, QUE-009)

- 작성일: 2026-11-14
- 작성: Claude (검토·승인 필요)
- 절차 간소화(소유자 요청): Phase 2 전체를 한 작업으로 묶고, Redis 계약 결정은 승인 대기 없이 설계·기록 후 구현했다(사후 승인 요청).
  Red 단계는 별도 실행하지 않았다(테스트와 구현을 함께 작성). 그 외 게이트(단위 테스트·정적 분석·경계 enforcer)는 그대로 통과시켰다.

## 1. 작업 ID
TASK-005 (Phase 2 전체)

## 2. 관련 요구사항 ID
QUE-008, QUE-009, NET-003(연결 설정 부분), NET-006(DMZ 경계 enforcer). Redis 구현에 대한 QUE-001~007 재검증.

## 3. 구현 내용
- **도메인 포트** `QueueOperations`: 메모리 `WaitingQueue` 와 Redis `RedisWaitingQueue` 가 같은 계약을 구현. `FailurePolicy`(기본 FAIL_CLOSED), `QueueUnavailableException` 추가.
- **신규 모듈** `openqueue-queue-infrastructure`(M04, DMZ): Lettuce 6.9.0. DB 드라이버·Flyway·control-api 의존 금지 enforcer.
- **key 설계**(`RedisKeySpace`): `oq:{site:queue}:` hash tag, seq / waiting / waiting_seen / admitted / admissions / session:* / admit:*.
- **Lua 스크립트 6종**(리소스 파일, Java 하드코딩 금지): register, heartbeat, cancel, expire_waiting, expire_leases, admit(멱등→lease 회수→대기 만료→입장률→선정).
  EVALSHA + NOSCRIPT 재로드(`RedisQueueScripts`).
- **연결**(`RedisConnectionSettings`/`RedisCredentials`/`RedisConnectionFactory`): ACL 필수, TLS 시 인증서 검증 강제, 명령 타임아웃, 비밀번호 마스킹, 연결 실패 → `QueueUnavailableException`.
- **보존 기간** `RedisRetentionPolicy`(종단 세션·멱등 결과 TTL, 기본값 없음).
- **빌드/CI**: failsafe `integration` 프로파일(`*IT.java`), CI `redis-integration` 잡(Ubuntu Docker, Testcontainers).

## 4. 변경 파일
- domain: 신규 `QueueOperations`, `FailurePolicy`, `QueueUnavailableException`; `WaitingQueue`(implements); 테스트 `FailurePolicyTest`
- infrastructure(신규 모듈): `pom.xml`, 8개 Java 클래스, `src/main/resources/lua/*.lua` 6개, 단위 테스트 3개, 통합 테스트 `RedisWaitingQueueIT`(15건)
- build: `backend/pom.xml`(모듈·버전·failsafe·프로파일), `.github/workflows/ci.yml`(redis-integration 잡)
- docs: 신규 `docs/database/redis-keys.md`, `SRS.md`(QUE-008/009 상세·상태), `traceability-matrix.md`, `module-design.md`(상태), `PROGRESS.md`, `CHANGELOG.md`, 증거 로그

## 5. 설계 변경 여부
- Redis 계약 신설(I3): D-QUE-008-1~7 (`redis-keys.md` §5) **승인 필요**.
- 포트 인터페이스를 `openqueue-queue-application`(미생성) 대신 도메인 모듈에 두었다(module-design §3 와 다름). application 모듈은 Phase 3 에서 유스케이스와 함께 만들며 그때 포트 이동 여부를 결정한다.
- 상위 아키텍처(Zone, 모듈 의존 방향) 변경 없음. infra → domain 단방향.

## 6. 영향도 분석
- 이전 단계: 도메인 76건 전부 통과. `WaitingQueue` 는 인터페이스 선언만 추가(동작 불변).
- 현재 단계: Redis 구현은 ms 정밀도. 종단 세션은 보존 기간 후 "미등록"으로 보임(메모리 모델과 차이, D-QUE-008-4). Lua 스크립트가 세션 key 를 내부 구성(D-QUE-008-2).
- 다음 단계: Phase 3 API 는 `QueueOperations` 만 의존하면 메모리/Redis 를 교체 가능. 재시도 정책은 API 계층 책임(D-QUE-008-5). Phase 7 에서 Sentinel/Cluster 클라이언트·failover 검증(D-QUE-008-6).
- 영향도 등급: **I3**(Redis 계약 신설) + I2(새 모듈 인터페이스).

## 7. 작성/수정한 테스트
| 테스트 | 건수 | AC | 실행 위치 |
|---|---|---|---|
| `RedisKeySpaceTest` | 2 | AC-QUE-008-1 | 로컬·CI 단위 |
| `RedisConnectionFactoryTest` | 5 | AC-QUE-008-2, AC-QUE-009-1, 설정 검증, 마스킹 | 로컬·CI 단위 |
| `LuaScriptTest` | 2 | 스크립트 로드·admit 처리 순서 | 로컬·CI 단위 |
| `FailurePolicyTest` (domain) | 2 | AC-QUE-009-2, 포트 구현 | 로컬·CI 단위 |
| `RedisWaitingQueueIT` | 15 | AC-QUE-001~007 재검증, AC-QUE-008-3/4/5 | **CI redis-integration 잡만** |
| 기존 도메인 테스트 (회귀) | 74 | AC-QUE-001~007 | 로컬·CI 단위 |

## 8. 실행한 테스트 명령
```bash
cd backend && mvn -B -ntp clean verify                 # 로컬: 단위 + 정적 분석 + enforcer (IT 제외)
cd backend && mvn -B -ntp -Pintegration verify         # CI redis-integration 잡 (Docker 필요)
```

## 9. 실제 테스트 결과
| 단계 | 결과 | 증거 |
|---|---|---|
| 로컬 1차 | Checkstyle LineLength 4건 → 줄바꿈 수정(동작 불변) | 콘솔 |
| 로컬 최종 | 도메인 **76/76**, 인프라 단위 **9/9**, BUILD SUCCESS | `docs/test/evidence/TASK-005-local-verify.log` |
| 통합 `RedisWaitingQueueIT` 15건 | **로컬 미실행**(Docker 없음). 컴파일은 통과. CI 푸시 후 `redis-integration` 잡 결과로 확정 | GitHub Actions |

**미실행 위험**: Lua 스크립트 논리 오류(문법·키 인덱스·경계 부등호)는 로컬에서 잡히지 않는다. CI 잡이 실패하면 스크립트를 수정하고 다시 푸시한다. CI 통과 전까지 QUE-008 검증 상태는 `진행중`이다.

## 10. 정적 분석 결과
Checkstyle 0건(두 모듈), SpotBugs 0건(두 모듈), enforcer: 의존성 수렴 통과, 도메인 순수성 통과, **DMZ 경계(DB 드라이버·Flyway·control-api 금지) 통과**. `-Xlint:all -Werror` 경고 0건.

## 11. 보안 검토 결과
- DMZ 모듈에 MariaDB 드라이버·내부 자격증명·서명 개인키 없음(enforcer 로 드라이버 금지 강제).
- ACL 자격증명 필수, TLS 시 인증서 검증 비활성화 옵션 없음, 비밀번호 toString 마스킹, 예외 메시지에 호스트·자격증명 미포함(테스트로 확인).
- 정책 값을 Redis 에 저장하지 않아 Redis 침해로 정책 변조 불가. Fail-open 은 기본값 아님.
- 통합 테스트 비밀번호는 컨테이너 전용 상수(`gitleaks:allow` 표기). 운영 값 아님.
- 잔여: TLS 실연결·ACL 권한 축소 계정은 서버 환경에서 검증(Phase 7).

## 12. 미해결 사항
- **CI `redis-integration` 결과 확인** 후 추적표 QUE-008 상태를 `통과(통합)` 으로 갱신해야 한다.
- 승인 요청: D-QUE-008-1~7, 포트 위치(domain vs application), Phase 1 결정 전체(D-QUE-001~007).
- Dependabot 이 lettuce/testcontainers 갱신 PR 을 올릴 것 — 메이저 변경은 통합 테스트 통과 후 병합.

## 13. 다음 작업 제안 — Phase 3 (Waiting Room·Queue API)
1. `openqueue-queue-application` 모듈: 유스케이스(등록·조회·heartbeat·취소·입장 주기), 시계 주입, 재시도 정책(D-QUE-008-5), `FailurePolicy` 적용 지점.
2. Queue API(Spring Boot): 세션 쿠키/ID 발급(추측 불가 128bit+), 입력 검증, 오류 매핑(api/error-and-logging-conventions.md), rate limit.
3. SSE 상태 갱신(DEC-05 권고안 SSE 채택 확인 필요), React Waiting Room(경량, UI 라이브러리 없음).
