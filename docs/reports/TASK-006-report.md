# TASK-006 보고서 — Phase 3 Waiting Room·Queue API

> 절차: `docs/claude.md` §3.4 토큰 효율 절차(2026-11-15 전환). 단위 작업 P3-01~10 은 `docs/CHECKPOINT.md` 로 추적했고 테스트는 Phase 게이트에서 1회 실행했다.

```text
[작업 결과]
1. 작업 ID: TASK-006 (Phase 3 전체)
2. 관련 요구사항 ID: QUE-010, QUE-011, QUE-012, QUE-013, WUI-001, WUI-002, QUE-009(API 계층), SEC-001, SEC-002, OBS-001(선반영)
3. 구현 내용:
   - openqueue-queue-application: WaitingRoomService(join/status/heartbeat/leave), AdmissionCycle(만료→lease 회수→입장),
     InMemoryQueueRegistry, SessionIdGenerator(SecureRandom 128bit base64url, 미등록 ID 재사용 금지)
   - openqueue-queue-api (Spring Boot 3.5.16): /queue/v1/sites/{siteId}/queues/{queueId}/session POST/GET/DELETE,
     /heartbeat, /events(SSE). 세션 쿠키 oq_session(HttpOnly/Secure/SameSite=Lax/Path=/queue/), RFC 9457 ProblemDetail
     + QUE-* 코드(instance 는 요청 ID URN — 입력 원문 미반영), X-Request-Id 필터, 주소별 분당 rate limit(429+Retry-After),
     저장소 장애 503 Fail-closed, SSE 가 heartbeat 겸용·비대기 상태에서 종료, 입장 주기 스케줄러, store=MEMORY|REDIS 조립
   - frontend/waiting-room-web (React 19 + Vite 7 + TS, UI 라이브러리 없음): 순번·대기 인원·예상 대기시간(클라이언트 추정),
     SSE → 영구 실패 시 5초 폴링 fallback, 만료/취소/gone/재진입 거부 안내와 재등록, 취소 버튼, 경로 /{site}/{queue}
   - Playwright E2E 4건(CI 전용, e2e 프로파일), CI 잡 frontend-test·frontend-e2e, Dependabot npm 추가
   - 문서: SRS QUE-010~013 명세·AC, docs/api/queue-api.md(계약·오류·D-API-001~006), CHECKPOINT 절차
4. 변경 파일:
   - 신규: backend/openqueue-queue-application/**, backend/openqueue-queue-api/**, frontend/waiting-room-web/**,
     docs/api/queue-api.md, docs/CHECKPOINT.md, docs/reports/TASK-006-report.md
   - 수정: backend/pom.xml(모듈 2개, Spring Boot BOM, -parameters), backend/config/spotbugs/spotbugs-exclude.xml(2건, 사유 기재),
     infrastructure RedisQueueScripts/RedisWaitingQueue(SHA 캐시 ConcurrentHashMap, 공유 연결 주석),
     .github/workflows/ci.yml, .github/dependabot.yml, docs/requirements/SRS.md, docs/PROGRESS.md, docs/claude.md §3.4,
     CLAUDE.md, docs/test/traceability-matrix.md
5. 설계 변경 여부: 있음(사후 승인 요청) — D-API-001~006 (queue-api.md §3). 모듈 경계 추가(application, api) 는 개발요청서 §6 구조와 일치.
   보안 경계: api 모듈은 DMZ 전용, MariaDB/Flyway/control-api 의존을 enforcer 로 금지(변경 없음). Zone 통신 변경 없음.
6. 영향도 분석 (I2 — 모듈 간 인터페이스 추가, 기존 공개 계약 불변):
   - 이전 단계: 도메인·Redis 어댑터 공개 API 변경 없음. 어댑터 스레드 안전성 주석·SHA 캐시만 보강(동작 동일). 회귀 통과.
   - 현재 단계: Queue API 계약(I1) 신설. 세션 식별은 쿠키만, 정책은 설정 파일(D-API-006).
   - 다음 단계: Phase 4 Gateway 가 /queue 경로 프록시·Secure 쿠키 전제(TLS)·신뢰 프록시 IP·Fail-open 분기를 담당(D-API-003/004).
     Phase 6 에서 openqueue.queues 설정을 서명 스냅샷으로 대체. 입장 후 토큰 발급은 Phase 6.
7. 작성/수정한 테스트:
   - application: WaitingRoomServiceTest(9), SessionIdGeneratorTest(1)
   - api(MockMvc): SessionControllerTest(9: 쿠키 속성·세션 ID 미노출, 재진입 순번 유지, 401/404, 400 원문 미반영,
     heartbeat/취소 멱등·재진입 거부, 입장 주기, SSE status·종료, SSE 401, X-Request-Id), RateLimitFilterTest(1), StoreUnavailableTest(1)
   - frontend(Vitest+RTL): queueApi.test.ts(5), waitEstimate.test.ts(8), WaitingRoom.test.tsx(9: 순번 표시·SSE 쿠키,
     status 갱신·ADMITTED, gone→재등록, 복구, 재진입 거부, 취소, 503, 폴링 전환, resolveScope)
   - e2e(Playwright, CI): waiting-room.spec.ts(4: 즉시 입장, 2번째 대기→lease 만료 후 입장, 새로고침 복구+취소, HttpOnly 쿠키)
8. 실행한 테스트 명령:
   cd backend && mvn -B -ntp -q clean verify ; mvn -B -ntp -q verify -pl openqueue-queue-api -am (수정 반복 후 최종)
   cd frontend/waiting-room-web && npm install && npx tsc -b && npx vitest run
9. 실제 테스트 결과 (2026-11-15 로컬, Windows):
   - mvn verify: BUILD SUCCESS. JUnit testcase 106건 실행(surefire XML 기준; 클래스 집계로는 domain 29 / infrastructure 9 /
     application 10 / api 11), 실패 0, 오류 0. Redis 통합(IT)은 CI -Pintegration 에서 실행.
   - Vitest: 3 파일 22건 통과. tsc: 오류 없음.
   - Playwright E2E: 로컬 미실행(Docker/브라우저 설치 없음, 정책상 CI 전용) → CI frontend-e2e 잡 결과로 확인. 미실행이므로 통과로 보고하지 않음.
   - 게이트 중 수정한 결함: Optional 빈 destroyMethod 로 컨텍스트 실패 → @ConditionalOnProperty 로 분리; -parameters 누락으로
     @PathVariable 전부 400; ProblemDetail instance 에 요청 경로 원문 노출(SEC-001) → 요청 ID URN; SSE 가 ADMITTED 에서 미종료;
     테스트 간 인메모리 대기열 공유 → @DirtiesContext; 장애 mock 이 register 에서 NPE.
10. 정적 분석 결과: Checkstyle 0건(8건 수정), SpotBugs 0건(제외 2건: SessionIdGenerator EI_EXPOSE_REP2 — SecureRandom 주입,
    RequestIdFilter HRS_REQUEST_PARAMETER_TO_HTTP_HEADER — 정규식 허용 목록 검증 후 echo, 사유 파일에 기재), enforcer 경계 통과, JaCoCo 생성.
11. 보안 검토 결과:
    - 세션 ID 는 HttpOnly 쿠키에만 존재, 응답 본문·로그에 미노출(테스트로 확인). 클라이언트가 보낸 미등록 ID 는 재사용하지 않음(세션 고정 방지).
    - 오류 응답에 입력 원문·호스트·예외 메시지·스택 미포함(테스트로 확인). 5xx 는 요청 ID 만.
    - rate limit 은 remoteAddr 기준, 프록시 헤더 불신(Phase 4 Gateway 가 신뢰 경계 담당).
    - DMZ 모듈에 DB 드라이버·내부 자격증명 없음(enforcer). Redis 비밀번호는 환경변수 주입.
    - 잔여: 쿠키 Secure 는 TLS 종단(Gateway/LB) 전제. SSE 연결 수 상한·사용자별 동시 스트림 제한 없음(Phase 7 HA 에서 검토).
12. 미해결 사항:
    - 사후 승인 요청: D-API-001~006, SpotBugs 제외 2건, QUE-010~013 명세(검토중), Phase 1~2 승인 묶음(기존)
    - CI 확인: frontend-test·frontend-e2e 잡 최초 실행 결과(package-lock.json 포함 커밋). E2E 실패 시 타이밍(lease 3s) 조정 가능.
    - 예상 대기시간은 클라이언트 측 추정(서버 처리량 미제공). Phase 5/7 에서 서버 통계로 대체 검토.
    - 포트 QueueOperations 위치(infra 유지) 그대로 둠.
13. 다음 작업 제안: Phase 4 Edge Gateway (TASK-007) — `docs/CHECKPOINT.md` P4-01 부터. Spring Cloud Gateway 2025.0.x(Boot 3.5 호환) 버전 고정 ADR 필요.
```

전체 진행률: **약 37 %** (`docs/PROGRESS.md` §0, Phase 3 항목 4/5 — E2E 게이트는 CI 확인 전까지 미완료).
