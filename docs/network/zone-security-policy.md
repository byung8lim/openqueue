# Zone별 네트워크 보안 정책

- 문서 버전: 0.1 (TASK-000, 보안팀·네트워크팀 검토 전 초안)
- 원본: 설계서 §3, §4 — 충돌 시 원본 설계서 우선
- 포트·프로토콜 상세: [port-matrix.md](port-matrix.md)

## 1. Zone 정의

| Zone | 구성요소 | 신뢰 수준 | 접근 원칙 |
|---|---|---|---|
| Internet | 사용자 브라우저 | 신뢰 안 함 | DMZ 공개 진입점(LB/WAF 443)만 접근 |
| DMZ | LB/WAF, Edge Gateway, Queue Engine, Waiting Room, Queue Redis | **침해 가정** | 외부 요청 처리. 내부 DB 접근 금지 |
| 내부 관리망 | Admin Web, Control API, 정책 서명·배포, IAM, 모니터링 | 높음 | VPN/Bastion 경유 승인 관리자만 |
| DB Zone | MariaDB, 감사 저장소, 백업 | 최고 | Control API 등 승인된 서비스 계정만 |
| 내부 서비스망 | Internal API Ingress, Business API, Origin | 높음 | 인터넷 직접 접근 금지, Ingress 경유 |

## 2. 기본 원칙

1. **기본 거부**: 아래 허용 목록 외 Zone 간 통신은 모두 차단한다 (NET-002).
2. **DMZ 침해 가정**: DMZ가 장악되어도 아래가 불가능해야 한다 (설계서 §4.2).
   - MariaDB 접속, 관리자 자격증명 획득, 임의 내부 IP/포트 접근, 임의 URL 프록시,
     내부 API 무제한 호출, 감사 로그 변경, 토큰 서명키 탈취
3. **DMZ 비밀정보 최소화**: 내부 DB 계정, 관리자 비밀정보, 업무 서비스 자격증명, 장기 서명 개인키를 DMZ에 두지 않는다 (NET-006).
4. **암호화**: 허용 통신은 TLS, 서비스 간은 mTLS (NET-005).
5. **심층 방어**: 애플리케이션 allowlist + egress 방화벽 + 계정 최소 권한을 모두 적용한다. 하나만으로 충분하다고 보지 않는다.

## 3. 허용 통신 목록 (요약)

| # | 출발지 | 목적지 | 판정 | 필수 통제 | 관련 요구사항 |
|---|---|---|---|---|---|
| A1 | Internet | DMZ LB/WAF | 허용 | WAF, DDoS 방어, TLS | - |
| A2 | DMZ LB | Edge Gateway | 허용 | 사설망, TLS | - |
| A3 | Edge Gateway | Queue Engine | 제한 허용 | 전용 포트, 서비스 인증 | DEC-01 |
| A4 | Queue Engine | Queue Redis | 허용 | TLS, ACL 전용 계정, 최소 명령 | NET-003 |
| A5 | Edge Gateway | Internal API Ingress | 제한 허용 | 목적지 allowlist, mTLS | NET-004/005 |
| A6 | Edge Gateway, Queue Engine | Distribution API (관리망) | 제한 허용 | mTLS 노드 인증서, 서명 스냅샷 읽기·노드 상태 쓰기만, Admin API와 분리, rate limit | POL-001/002/004, GAT-004, DEC-02/06, ADR-0009 |
| A7 | 내부 모니터링 | DMZ metrics endpoint | 제한 허용(Pull) | 인증, IP 제한, 읽기 전용 (로그는 별도 구성, ADR-0004) | DEC-11 |
| A10 | Queue Engine | 내부 토큰 서명 서비스 | 제한 허용 (소유자 승인) | mTLS, claim 제한, 발급량 상한, 감사 | DEC-03, ADR-0005 |
| A8 | Control API | MariaDB | 허용 | DB 방화벽, TLS, 최소 권한 계정 | DB-001 |
| A9 | Internal Ingress | Business API | 허용 | 내부 인증·접근 제어 | TOK-002 |

## 4. 명시적 차단 목록

| # | 출발지 | 목적지 | 검증 방법(예정) |
|---|---|---|---|
| D1 | Internet | Queue Redis / 관리망 / DB Zone / 내부 서비스망 | 외부 포트 스캔 (Phase 8) |
| D2 | DMZ 전체 | DB Zone | DMZ 노드에서 MariaDB 포트 연결 시도 실패 (Phase 6) + 빌드 규칙(ADR-0003) |
| D3 | DMZ | 내부 관리망 (A6/A10 외, Admin Control API 포트 포함) | 연결 시도 실패 (Phase 6) |
| D4 | Edge Gateway | 미등록 목적지 / loopback / link-local / metadata | SSRF 테스트 (Phase 4) |
| D5 | Internet | Business API / Origin | 직접 접속 실패 (Phase 8) |

## 5. 미결정 사항

DEC-02/06(Distribution API, ADR-0009)·DEC-03(ADR-0005)·DEC-08·DEC-11은 소유자 결정이 났다.
DMZ → 관리망 통신 두 건(A6 Distribution API, A10 토큰 서명)은 소유자 승인 상태이며, 보안·네트워크팀 검토를 거쳐 Phase 6에서 구현한다.
추적: [open-decisions.md](../decisions/open-decisions.md).
