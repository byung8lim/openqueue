# CLAUDE.md

이 저장소에서 작업하는 Claude는 아래 문서를 **작업 전에 반드시** 따른다.

1. 개발요청서: [docs/claude.md](docs/claude.md) — 개발 원칙, TDD 절차, 보고 형식(§3.3), Definition of Done(§13)
2. 아키텍처·보안 설계서: [docs/openqueue_architecture_security_design_ko.md](docs/openqueue_architecture_security_design_ko.md)
3. 기여 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)

## 핵심 규칙 요약

- 승인된 요구사항 ID([SRS](docs/requirements/SRS.md)) 없이 기능을 구현하지 않는다. 모호하면 질문한다.
- 테스트 먼저(Red) → 최소 구현(Green) → `cd backend && mvn verify` 통과. 테스트 삭제·약화 금지.
- 주요 로직·보안·동시성·상태 전이·예외 주석은 한국어. SQL은 Java에 하드코딩하지 않는다.
- 사람의 승인 없이 변경 금지: Zone 경계, 인증·인가, 키·서명 방식, DB/Redis/API/정책/토큰 계약, 모듈·배포 경계, Fail-open/closed.
- DMZ 모듈에 MariaDB 드라이버·내부 자격증명·서명 개인키를 추가하지 않는다. 임의 URL 프록시 금지.
- 미결정 사항: [docs/decisions/open-decisions.md](docs/decisions/open-decisions.md) — 결정 전 해당 영역 구현 금지.
- 작업 후 [docs/reports/](docs/reports/)에 §3.3 형식 보고서 작성, [추적표](docs/test/traceability-matrix.md) 갱신.
- 실행하지 못한 테스트는 통과로 보고하지 않는다.

## 명령

```bash
cd backend
mvn test     # 단위 테스트
mvn verify   # 전체 게이트 (enforcer 경계 + 테스트 + Checkstyle + SpotBugs + JaCoCo)
```
