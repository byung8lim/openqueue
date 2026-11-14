# Changelog

형식: [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/), 버전: [Semantic Versioning](https://semver.org/lang/ko/)

## [Unreleased]

### Added
- MIT 라이선스(OD-01), 패키지 `com.wbl.openqueue`(OD-02), 로컬 게이트 스크립트 `scripts/verify.sh`(OD-03)
- ADR-0004~0007: 배포 토폴로지, 토큰 서명·Ingress, 관리자 인증, 런타임 라우트 관리 (소유자 1차 결정 반영)
- ADR-0009: DMZ ↔ 내부망 Distribution API(DEC-02/06 승인), ADR-0008(Redis 경유)은 기각 기록. ADR-0005/0006/0007 승인 반영,
  SRS POL-004·ADM-004·GAT-004 갱신, 포트 매트릭스 A6 재정의
- OD-04: CI Action 커밋 SHA 고정. OD-05 권고안 채택: `.github/dependabot.yml`(github-actions, maven). OD-09: Admin Web UI는 Ant Design
- TASK-000: 저장소 구조, Maven 멀티모듈 빌드, Checkstyle/SpotBugs/JaCoCo, 모듈 경계 enforcer 규칙
- TASK-000: CI 파이프라인(GitHub Actions), PR 템플릿, 문서·ADR·요구사항 템플릿
- TASK-000: Zone 보안 정책, 포트 매트릭스, 모듈 설계, 요구사항 추적표
- QUE-001 (샘플): FIFO 입장 후보 선정 및 재전송 멱등성 도메인 모델 (`openqueue-queue-domain`)
