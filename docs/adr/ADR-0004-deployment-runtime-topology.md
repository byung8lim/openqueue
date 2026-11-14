# ADR-0004: 배포·런타임 토폴로지 (DEC-01, DEC-04, DEC-10, DEC-11)

- 상태: 승인 (프로젝트 소유자 결정 2026-11-14)
- 영향도 등급: I3 (배포 경계, Redis key 설계에 영향)

## 1. DEC-01 — Gateway와 Queue Engine은 별도 프로세스

- `openqueue-gateway`와 Queue Engine(`openqueue-queue-*`)은 **서로 다른 실행 산출물**(jar/이미지)로 빌드·배포한다.
- 둘 사이 통신은 port-matrix A3(mTLS)만 사용한다. 같은 JVM 안에서 직접 호출하는 구조를 만들지 않는다.
- 이유: Gateway 침해 시 Redis 자격증명에 바로 닿지 않게 하고(Redis 계정은 Queue Engine만 보유), 각각 독립 확장한다.

## 2. DEC-04 — Redis 연결 모드 3종 선택 지원

| 모드 | 용도 | 설정 |
|---|---|---|
| Standalone | 개발·소규모 | 단일 노드 |
| Sentinel | 고가용성(자동 failover) | master name + sentinel 목록 |
| Cluster | 수평 확장 | 노드 목록 |

설계 제약 (Phase 2, QUE-008에 반영):

1. **Cluster 호환 key 설계**: Lua Script가 한 번에 다루는 key는 같은 hash slot에 있어야 한다.
   key 형식은 `oq:{<site_id>:<queue_id>}:<자료구조>` 처럼 **대기열 범위를 hash tag `{}`로 묶는다.**
   모든 모드에서 같은 key 형식을 사용해 모드 전환 시 코드 변경이 없게 한다.
2. **failover 시 초과 입장 방지**: Sentinel/Cluster는 비동기 복제라 failover 직후 최근 쓰기가 유실될 수 있다.
   입장 카운터가 실제보다 작아지면 동시 입장 상한을 초과할 수 있으므로(설계서 §7.3) 복구 정책을 QUE-008 명세에서 정의한다.
   (후보: failover 감지 시 lease TTL 동안 신규 입장 보류 / lease 집합 기준 카운터 재계산)
3. 세 모드 모두 TLS + ACL 사용자 인증을 지원해야 한다(NET-003). 모드별 통합 테스트를 Testcontainers로 작성한다.

## 3. DEC-10 — 배포 형태 3종 지원

| 형태 | 산출물 | 비고 |
|---|---|---|
| Linux 프로세스 | 실행 jar + systemd unit 예시 + 환경 설정 파일 | 전용 OS 서비스 계정, 파일 권한 0640/0400 |
| Docker / Podman | OCI 이미지 (non-root, read-only rootfs 지향) | compose 예시는 개발용으로만 제공 |
| Kubernetes | 매니페스트 또는 Helm chart | Zone별 namespace/NetworkPolicy 분리 |

공통 원칙:

- 설정·비밀정보는 **환경변수 또는 외부 파일 경로로 주입**하며, 세 형태가 같은 설정 키를 사용한다.
- 어떤 형태에서도 DMZ 구성요소와 내부 관리망 구성요소를 같은 배포 단위(호스트 프로세스 그룹, Pod, 서비스 계정)로 묶지 않는다.
- 배포 정의는 `deployment/<zone>/{systemd,docker,k8s}/`에 둔다. (Phase 7, OPS-002)

## 4. DEC-11 — 로그 수집은 별도 구성

- OpenQueue 애플리케이션은 **구조화(JSON) 로그를 표준 출력 또는 로컬 롤링 파일로만 생성**한다(형식: error-and-logging-conventions.md).
- 수집·전달·저장(Fluent Bit, Filebeat, 기업 로그 플랫폼 등)은 **운영 환경에서 별도로 구성**하며 OpenQueue 범위 밖이다.
- 따라서 애플리케이션이 DMZ에서 내부망으로 로그를 직접 전송하는 기능을 만들지 않는다(DMZ→내부 연결 미추가).
- 관리자 감사 로그(DB Zone 저장)는 이 결정과 별개로 Control API가 기록한다.

## 결과와 영향

- QUE-008(Redis key) 명세 작성 시 §2 제약을 수용 기준에 포함해야 한다.
- port-matrix의 A7(로그/지표 Pull)에서 로그 항목 제거, 지표만 유지.
