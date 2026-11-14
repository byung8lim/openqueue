# 출발지·목적지·포트 매트릭스

- 문서 버전: 0.1 (TASK-000 초안)
- **주의**: 포트 번호는 개발·검토용 **제안값**이다. 실제 값은 배포 환경과 네트워크팀 승인으로 확정한다.
  확정 전 이 표를 방화벽 정책으로 그대로 사용하지 않는다.

| ID | 출발지 (Zone) | 목적지 (Zone) | 프로토콜 | 제안 포트 | 인증 | 방향 | 상태 |
|---|---|---|---|---|---|---|---|
| A1 | Internet | LB/WAF (DMZ) | HTTPS | 443 | - | 인바운드 | 초안 |
| A2 | LB/WAF (DMZ) | Edge Gateway (DMZ) | HTTPS | 8443 | TLS | DMZ 내부 | 초안 |
| A3 | Edge Gateway (DMZ) | Queue Engine (DMZ) | HTTPS | 9443 | mTLS | DMZ 내부 | 초안 (DEC-01) |
| A4 | Queue Engine (DMZ) | Queue Redis (DMZ 전용 서브넷) | Redis+TLS | 6380 | ACL 사용자 + TLS | DMZ 내부 | 초안 |
| A5 | Edge Gateway (DMZ) | Internal API Ingress (내부 서비스망) | HTTPS | 443 | mTLS | DMZ → 내부 | 초안 |
| A6 | Edge Gateway / Queue Engine (DMZ) | Distribution API (관리망, Admin Control API와 별도 프로세스) | HTTPS (+SSE) | 9444 | mTLS 노드별 인증서, 스냅샷 읽기·상태 쓰기만 | **DMZ → 관리망** | 소유자 승인 (DEC-02/06, ADR-0009), 보안·네트워크팀 검토 권장 |
| A7 | 모니터링 (관리망) | Metrics endpoint (DMZ) | HTTPS | 9445 | mTLS 또는 토큰, IP 제한 | 관리망 → DMZ (Pull) | 초안 (지표만. 로그 전달은 별도 구성, DEC-11/ADR-0004) |
| A8 | Control API (관리망) | MariaDB (DB Zone) | MariaDB+TLS | 3306 | 전용 DB 계정 + TLS | 관리망 → DB Zone | 초안 |
| A9 | Internal Ingress (내부 서비스망) | Business API (내부 서비스망) | HTTPS | 기존 환경 | 기존 내부 인증 | 내부 | 기존 환경 따름 |
| A10 | Queue Engine (DMZ) | 토큰 서명 서비스 (관리망) | HTTPS | 9446 | mTLS, site/queue 범위·발급량 제한 | **DMZ → 관리망** | 소유자 승인 (DEC-03 권고안 채택, ADR-0005 §3), 보안팀 검토 권장 |
| M1 | 관리자 PC (VPN/Bastion) | Admin Web / Control API (관리망) | HTTPS | 443 | OIDC + MFA | 관리망 내부 | 초안 (DEC-07) |

**위 표에 없는 모든 Zone 간 통신은 차단한다.** 행 추가는 I4 변경이며 보안 승인이 필요하다.
