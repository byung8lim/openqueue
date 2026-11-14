# 보안 정책

## 1. 지원 버전

OpenQueue는 현재 Phase 0(개발 기반 구축) 단계이며 운영 배포를 지원하지 않는다.
릴리스 이후 지원 버전 표는 이 절에 추가한다.

## 2. 취약점 신고

- 공개 이슈 트래커에 취약점을 등록하지 않는다.
- 신고 채널: **프로젝트 소유자에게 비공개로 전달**한다 (OD-06). 저장소(`github.com/byung8lim/openqueue`)의
  Private vulnerability reporting(Security 탭 → Report a vulnerability)을 함께 사용한다. 저장소 설정에서 이 기능을 켜야 한다.
- 신고 시 포함할 내용: 영향 버전, 재현 절차, 영향 범위(Zone/구성요소), 가능한 경우 PoC.

## 3. 보안 설계 기준 (요약)

| 원칙 | 기준 |
|---|---|
| Zone 분리 | DMZ / 내부 관리망 / DB Zone / 내부 서비스망. Zone 간 기본 차단 |
| DMZ 무신뢰 | DMZ에 내부 DB 계정·관리자 자격증명·장기 서명 개인키를 두지 않는다 |
| 정책 신뢰 | 정책은 내부에서 서명, DMZ는 검증 후 원자 적용, 실패 시 마지막 정상 정책 유지 |
| 토큰 | 비대칭 서명 우선, 짧은 만료, `jti` 재사용 방지, 내부 Ingress에서 최종 검증 |
| 라우팅 | 서비스 ID allowlist만 허용, 임의 URL 프록시 금지, SSRF/DNS rebinding 방어 |
| 로그 | 토큰 원문·비밀정보·세션 ID 원문 기록 금지 |

상세: [zone-security-policy.md](docs/network/zone-security-policy.md), [원본 설계서](docs/openqueue_architecture_security_design_ko.md)

## 4. 개발 보안 규칙

- 비밀정보는 저장소에 커밋하지 않는다. CI의 비밀정보 탐지(gitleaks) 게이트가 검사한다.
- 운영 비밀정보는 Vault/KMS 또는 조직 표준 Secret Manager로 외부 주입한다.
- 의존성 추가 시 라이선스와 알려진 취약점(CVE)을 확인하고 PR에 기록한다.
- 보안 경계 변경(I4)은 보안 설계 승인과 보안 테스트 없이 병합하지 않는다.
