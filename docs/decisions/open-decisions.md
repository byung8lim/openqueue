# 미결정 사항 (Open Decisions)

- 갱신: 2026-11-14 (소유자 1~3차 결정 반영)
- 원칙: 결정 전에는 해당 영역을 구현하지 않는다. 결정되면 ADR로 옮기고 이 표의 상태를 갱신한다.
- 상태 값: `미결정` / `결정` / `결정(세부 확인 중)` / `권고안 채택` / `보류`

## 1. 설계서 §16 의사결정 (보안·네트워크)

| ID | 결정 항목 | 소유자 결정 (2026-11-14) | 반영 문서 | 상태 |
|---|---|---|---|---|
| DEC-01 | Gateway와 Queue Engine 배포 분리 | 별도 프로세스 | ADR-0004 §1 | 결정 |
| DEC-02 | DMZ 정책 배포 방식 | Redis 큐 방식은 탈취 위험으로 **기각**. DEC-06 요건까지 충족하는 **DMZ ↔ 내부망 양방향 인터페이스**(Distribution API) 추가 | ADR-0009 (ADR-0008 기각 기록) | 결정 (보안·네트워크팀 검토 권장) |
| DEC-03 | 입장 토큰 서명 위치·알고리즘 | 권고안 채택: 내부 KMS 원칙 + 관리망 암호화 키 파일 임시 대안, Ed25519 → ES256 순 | ADR-0005 | 결정 (기존 GW/LB의 Ed25519 지원 확인은 Phase 6 전) |
| DEC-04 | Redis HA | Standalone / Sentinel / Cluster 모두 선택 가능 | ADR-0004 §2, SRS QUE-008 | 결정 |
| DEC-05 | 실시간 대기 갱신 | (미응답) 설계서 권장 SSE | - | 권장안 채택 예정 (Phase 3 전) |
| DEC-06 | Gateway 구현 | Spring Cloud Gateway. 라우트는 YAML이 아닌 DB가 원천이며, **내부 API 서버가 Gateway에 필요한 인터페이스를 중개** | ADR-0007(승인), ADR-0009 | 결정 |
| DEC-07 | Admin 인증 | 내장 Admin: 최초 부트업 시 비밀번호 생성 → `initpassword.txt` 저장 → 최초 로그인 시 변경 강제. 일반 Admin: Keycloak/OIDC + MFA | ADR-0006(승인), SRS ADM-001/004 | 결정 (내장 계정 MFA 여부만 §2.1) |
| DEC-08 | 내부 Ingress 구현 | 기존 API Gateway/LB 사용 | ADR-0005 §4, SRS TOK-004 | 결정 |
| DEC-09 | 장애 정책 Fail-open/closed | 사이트별 설정, 기본 Fail-closed | SRS QUE-009 | 결정 |
| DEC-10 | 배포 플랫폼 | Linux 프로세스(systemd), Docker, Kubernetes 모두 지원 | ADR-0004 §3, SRS OPS-002 | 결정 |
| DEC-11 | DMZ 로그 전달 | 로그 수집은 별도 구성 (앱은 stdout/파일 JSON 로그만) | ADR-0004 §4 | 결정 |
| DEC-12 | 서비스 식별 | 설계서 기본안 그대로 (`site_id` + `service_id` + `queue_id`) | SRS D-QUE-001-3, §3.2 | 결정 |

## 2. 남은 세부 확인

### 2.1 DEC-07 — 내장 Admin 계정 MFA (Phase 5 전까지 답하면 됨)

내장 계정은 id/password로 확정. 추가로 **TOTP MFA를 요구할지**만 미정. 권장: 설정으로 선택 가능, 운영 환경 기본 활성.
내장 계정 수는 1개로 제한한다(ADR-0006 §3 권장안을 기본값으로 적용, 이견 있으면 알려주세요).

### 2.2 DEC-02/06 — Distribution API 핵심 (ADR-0009 요약)

- 내부 관리망에 **Distribution API**(Admin Control API와 별도 프로세스·포트)를 두고, DMZ의 Gateway·Queue Engine은 이 API에서
  서명된 라우트·정책 스냅샷을 받아 메모리에 원자 교체한다. DB에는 접속하지 않는다.
- "양방향"은 **DMZ → 관리망 mTLS 연결 하나** 위에서 구현: DMZ가 스냅샷을 가져오고(GET), 서버가 SSE로 변경을 푸시하고, DMZ가 적용 상태를 보고(PUT).
  관리망이 DMZ 노드로 연결을 여는 방향은 두지 않는다(노드마다 수신 포트 불필요).
- DMZ 침해 대비: API는 읽기(서명본·키)와 상태 쓰기만 노출, 전용 읽기 DB 계정, 노드별 인증서, 공개키는 설정 주입, 서명 검증은 그대로, 로컬 캐시, 캐시 없으면 Fail-closed.
- 보안·네트워크팀 검토 권장 사항: A6(DMZ → 관리망 9444), A10(DMZ → 관리망 9446).

## 3. 설명 — DEC-02, DEC-12 (기록 보존)

### 3.1 DEC-02 정책 배포 방식이란

라우트·대기열 정책은 **내부 관리망**에서 만들고 서명하지만, 실제로 쓰는 곳은 **DMZ**다. 둘 사이에는 방화벽이 있어
"어느 방향으로 연결을 열어 넘길지"를 정해야 한다.

| 선택지 | 방식 | 결과 |
|---|---|---|
| A. 내부 Push(HTTP) | 관리망이 DMZ Engine의 수신 포트로 mTLS 전송 | 노드마다 수신 포트 필요. 미채택 |
| B. DMZ Pull | DMZ가 관리망 API를 조회 | **채택** — 단, 전용 Distribution API로 범위를 좁히고 SSE로 푸시 알림을 결합(ADR-0009) |
| C. DMZ 저장소(Redis) 경유 | 관리망이 DMZ Redis에 기록, DMZ가 구독 | 소유자 기각(Redis 탈취 위험). ADR-0008 |
| D. 단방향 중계 장비 | 데이터 다이오드 등 | 장비·비용. 미채택 |

### 3.2 DEC-12 서비스 식별이란

여러 사이트를 한 OpenQueue로 운영할 때 "누구의, 어느 대기열, 입장 후 어디로"를 가리키는 ID 구조다.

| ID | 뜻 | 예 | 형식 (D-QUE-001-3) |
|---|---|---|---|
| `site_id` | 보호 대상 사이트(고객·업무 단위) | `ticket` | `^[a-z0-9][a-z0-9-]{0,63}$` |
| `queue_id` | 사이트 안의 대기열 | `concert-2026` | 동일 |
| `service_id` | 입장 후 Gateway가 보낼 내부 백엔드(allowlist 항목) | `ticket-api` | 동일 규칙 적용 예정 (GAT-002, Phase 4) |

입장 토큰은 `site_id`·`queue_id`를 claim으로 담고, Gateway는 `service_id`로만 목적지를 고른다(URL 직접 지정 금지).

## 4. 프로젝트 결정 사항 (TASK-000 중 발견)

| ID | 항목 | 소유자 결정 | 반영 | 상태 |
|---|---|---|---|---|
| OD-01 | 오픈소스 라이선스 | MIT | `LICENSE`, README | 결정 |
| OD-02 | Maven groupId / 패키지명 | `com.wbl.openqueue` | 코드·POM·문서 전환, 회귀 34/34 통과 | 결정 |
| OD-03 | 저장소 호스팅·CI | GitHub `byung8lim/openqueue` | 원격 `origin`, `scripts/verify.sh`(CI와 동일 명령) | 결정 (최초 CI 결과 확인 = TASK-001) |
| OD-04 | CI 서드파티 Action 고정 방식 | 커밋 해시로 고정 | `.github/workflows/ci.yml` 적용(§5), Dependabot github-actions | 결정·적용 |
| OD-05 | 의존성 취약점 검사 도구 | 권고안 채택 | Dependabot maven 추가(§6). Trivy는 Phase 2 | 권고안 채택 |
| OD-06 | 취약점 신고 채널 | 프로젝트 소유자 | `SECURITY.md` | 결정 |
| OD-07 | 커버리지 임계치 | 개발 중 미적용 | Phase 8 전 재검토 | 보류 |
| OD-08 | 클라이언트 IP·세션 로그 보존 기간 | 개발 중 미적용 | Phase 7(중앙 로그) 전 재검토 | 보류 |
| OD-09 | 관리자 UI 라이브러리 | Ant Design | §7 (변경 가능 시점 포함) | 결정 |

## 5. OD-04 — Action 커밋 해시 고정 (적용 완료)

`actions/checkout@v4` 의 `v4`는 **태그**라서 제작자(또는 계정 탈취자)가 가리키는 내용을 바꿀 수 있다.
커밋 해시로 고정하면 내용이 바뀔 수 없다. 적용한 값(2026-11-14 `git ls-remote`로 확인):

| Action | 태그 | 고정 해시 |
|---|---|---|
| actions/checkout | v4.4.0 | `11d5960a326750d5838078e36cf38b85af677262` |
| actions/setup-java | v4.9.1 | `cf277c60eb25467037889841efdb72551f06f6c3` |
| actions/upload-artifact | v4.6.2 | `ea165f8d65b6e75b540449e92b4886f43607fa02` |
| gitleaks/gitleaks-action | v2.3.9 | `ff98106e4c7b2bc287b24eaf42907196329070c7` |

해시 고정은 자동 업데이트가 멈추는 부작용이 있어 Dependabot(`github-actions`)이 새 버전 PR을 올리도록 했다.
주석의 버전(`# v4.4.0`)은 Dependabot이 해시와 함께 갱신한다. 동작은 최초 푸시 후 CI에서 확인한다.

## 6. OD-05 — 의존성 취약점 검사 (권고안 채택)

| 시점 | 적용 | 상태 |
|---|---|---|
| GitHub 푸시 시 | **Dependabot** `maven`(backend) + `github-actions`. 무료, NVD API 키 불필요, 취약 라이브러리 알림 + 업그레이드 PR | `.github/dependabot.yml` 적용 |
| Phase 3 | Dependabot `npm`(frontend) 추가 | 예정 |
| Phase 2 (첫 런타임 의존성) | CI에 **Trivy** 파일시스템 스캔 잡, **비차단(리포트만)** | 예정 |
| Phase 7 | Trivy **차단 게이트**(CRITICAL/HIGH, 수정판 있는 것만) — 개발요청서 §8.3 "의존성 취약점 정책 통과" | 예정 |
| Phase 8 | Trivy 이미지 스캔 + Syft SBOM | 예정 |

OWASP Dependency-Check는 NVD API 키·느린 DB 갱신 문제로 CI 게이트로 쓰지 않는다.

## 7. OD-09 — Ant Design 결정과 변경 가능 시점

- **결정**: Admin Web(내부 관리자 화면)은 Ant Design.
- **Waiting Room(외부 사용자 대기 화면)에는 쓰지 않는다**: 접속 폭주 시 수만 명이 받는 페이지라 번들을 최소로 유지해야 하므로
  UI 라이브러리 없이 경량으로 만든다(Phase 3). 따라서 OD-09는 Admin Web에만 영향을 준다.
- **바꿀 수 있는 시점**: Admin Web 코드는 **Phase 5에 처음 작성**된다. 그 전(Phase 1~4)에는 Ant Design에 의존하는 코드가 없으므로
  **비용 0으로 변경 가능**하다. Phase 5에서 첫 화면을 만든 뒤에 바꾸면 만든 화면을 다시 작성해야 한다.
  → Phase 5 착수 직전에 한 번 더 확인하고 그 뒤에는 고정한다.
