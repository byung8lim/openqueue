# OpenQueue

Java 기반 독립형 **Virtual Waiting Room**. 웹사이트·API에 접속이 집중될 때 사용자를 대기열에 등록하고,
정책에 따라 순차적·제한적으로 원본 서비스에 입장시킨다.

> 현재 상태: **Phase 0 (TASK-000) — 개발 기반 구축 단계**. 운영 사용 불가.
> 진행 현황은 [docs/test/traceability-matrix.md](docs/test/traceability-matrix.md)와
> [docs/reports/](docs/reports/)를 참고한다.

## 1. 핵심 설계 원칙

- DMZ / 내부 관리망 / DB Zone / 내부 서비스망을 분리하고 **DMZ 침해를 가정**한다.
- DMZ(Data Plane)는 MariaDB에 접근하지 않는다. 정책은 내부(Control Plane)에서 **서명된 스냅샷**으로 받는다.
- 동적 라우팅은 등록된 백엔드 **서비스 ID allowlist**만 사용한다. 임의 URL 프록시 금지.
- 입장 토큰은 비대칭 서명을 우선하며, 내부 API Ingress에서 최종 검증한다.

상세: [아키텍처](docs/architecture/architecture.md) · [모듈 설계](docs/architecture/module-design.md) ·
[Zone 보안 정책](docs/network/zone-security-policy.md) · [원본 설계서](docs/openqueue_architecture_security_design_ko.md) ·
[개발요청서](docs/claude.md)

## 2. 개발 환경 요구사항

| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | 21 이상 (대상 런타임 21 LTS) | 상위 JDK로 빌드해도 `--release 21`로 컴파일된다 |
| Maven | 3.9.0 이상 | enforcer가 검사한다 |
| Node.js | 22 LTS 이상 | Phase 3(프론트엔드)부터 필요 |
| Docker 또는 Podman | 최신 안정 버전 | Phase 2(Testcontainers 통합 테스트)부터 필요 |

## 3. 빌드 및 테스트

저장소: <https://github.com/byung8lim/openqueue>

```bash
git clone https://github.com/byung8lim/openqueue.git
cd openqueue/backend

# 단위 테스트만 빠르게
mvn test

# 전체 게이트: 빌드 + 단위 테스트 + 모듈 경계(enforcer) + Checkstyle + SpotBugs + 커버리지 리포트
mvn verify
```

산출물 위치:

| 산출물 | 경로 |
|---|---|
| 테스트 결과 | `backend/<module>/target/surefire-reports/` |
| 커버리지 | `backend/<module>/target/site/jacoco/index.html` |
| Checkstyle | `backend/<module>/target/checkstyle-result.xml` |
| SpotBugs | `backend/<module>/target/spotbugsXml.xml` |

`mvn verify`가 실패하면 PR을 병합할 수 없다(CI 필수 게이트).

## 4. 저장소 구조

```text
openqueue/
├── backend/                  Maven 멀티모듈 (Java 21)
│   ├── config/               Checkstyle, SpotBugs 규칙
│   └── openqueue-queue-domain/   대기열 도메인(순수 Java, DMZ)
├── frontend/                 (Phase 3) admin-web, waiting-room-web
├── database/                 (Phase 5) Flyway migration SQL
├── deployment/               Zone별 배포 정의 (dmz / management / db-zone / internal-ingress)
├── tests/                    모듈 횡단 테스트 (integration / contract / security / performance / e2e)
├── docs/                     요구사항·아키텍처·ADR·네트워크·테스트·운영 문서
└── .github/                  CI 파이프라인, PR 템플릿
```

모듈은 해당 Phase가 시작될 때 추가한다. 예정 모듈과 의존 규칙은 [module-design.md](docs/architecture/module-design.md) 참조.

## 5. 개발 절차

모든 기능은 **요구사항 ID → 수용 기준 → 테스트(Red) → 구현(Green) → 리팩터링 → 정적 분석 → 리뷰 → 추적표 갱신** 순서를 따른다.
상세 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 따른다.

## 6. 보안

취약점 신고 및 보안 규칙은 [SECURITY.md](SECURITY.md)를 참고한다. 저장소에 비밀정보를 커밋하지 않는다.

## 7. 라이선스

[MIT License](LICENSE). 의존성을 추가할 때는 MIT와 호환되는 라이선스인지 확인한다.
