# OpenQueue 아키텍처 및 네트워크 보안 설계서

-   문서 버전: 0.1
-   작성 기준: 지금까지 논의한 요구사항과 설계 방향
-   대상 시스템: Java 기반 오픈소스 Virtual Waiting Room(가칭 OpenQueue)
-   핵심 설계 원칙: **DMZ, 내부망, DB Zone 간 보안 경계와 최소 신뢰(Zero
    Trust)를 최우선으로 설계한다.**
-   상태: 아키텍처 초안 --- 구현 전 보안팀·네트워크팀 검토 및 요구사항
    확정 필요

------------------------------------------------------------------------

## 1. 목적 및 범위

OpenQueue는 특정 웹사이트에 접속이 집중될 때 사용자를 대기열에 등록하고,
정해진 입장 정책에 따라 백엔드 서비스로 순차 또는 제한적으로 진입시키는
독립형 Virtual Waiting Room이다.

목표는 다음과 같다.

1.  기존 웹사이트의 비즈니스 로직 변경을 최소화한다.
2.  여러 웹사이트와 서비스에 대기열 정책을 적용할 수 있도록 멀티 사이트
    구조를 지원한다.
3.  FIFO 기반 대기 순서, 동시 입장 제한, 대기 상태 조회, 입장 토큰
    검증을 제공한다.
4.  운영 중 서비스·백엔드·라우팅·대기열 정책을 동적으로 변경한다.
5.  DMZ 침해가 내부 관리망 또는 DB Zone 침해로 확대되지 않도록 네트워크
    및 신뢰 경계를 분리한다.
6.  설치·운영·확장이 가능한 오픈소스 기반의 독립 제품으로 구현한다.

이 문서는 네트워크 보안 정책, 논리 아키텍처, 오픈소스 패키지 후보, 기능
모듈 및 기능 목록을 정의한다. 상세 API 계약, DB 스키마, 화면 설계, 배포
파라미터는 후속 상세 설계에서 확정한다.

## 2. 최상위 보안 원칙

### 2.1 보안 설계 원칙

  -------------------------------------------------------------------------------
  ID                      원칙                    설계 및 운영 기준
  ----------------------- ----------------------- -------------------------------
  SEC-01                  네트워크 Zone 분리      DMZ, 내부 관리망, DB Zone, 내부
                                                  서비스망을 별도 보안 영역으로
                                                  구성한다.

  SEC-02                  기본 거부               Zone 간 통신은 기본 차단하고,
                                                  명시적으로 승인된
                                                  출발지·목적지·포트·프로토콜만
                                                  허용한다.

  SEC-03                  최소 권한               서비스 계정, DB 계정, Redis
                                                  ACL, API 권한을 기능별 최소
                                                  권한으로 부여한다.

  SEC-04                  DMZ 무신뢰              DMZ의 Gateway와 Queue Engine은
                                                  침해될 수 있다고 가정한다.
                                                  DMZ가 내부망의 신뢰 주체가 되지
                                                  않도록 한다.

  SEC-05                  관리 Plane 분리         관리자 UI, Control API, 정책
                                                  DB는 내부 관리망 및 DB Zone에
                                                  둔다. DMZ에 관리용 DB
                                                  접속정보를 배치하지 않는다.

  SEC-06                  데이터 최소화           DMZ Redis에는 대기열 운영에
                                                  필요한 최소 상태만 저장한다.
                                                  개인정보·업무 데이터·관리
                                                  데이터를 저장하지 않는다.

  SEC-07                  종단 간 검증            입장 토큰은 DMZ에서
                                                  발급되었다는 이유만으로
                                                  신뢰하지 않는다. 내부 API 진입
                                                  지점에서 서명·대상·만료·재사용
                                                  여부를 검증한다.

  SEC-08                  암호화                  Zone 간 허용 통신은 TLS를
                                                  기본으로 하고, 서비스 간 인증은
                                                  mTLS 적용을 원칙으로 한다.

  SEC-09                  목적지 제한             동적 라우팅의 목적지는 사전
                                                  등록된 허용 대상만 사용한다.
                                                  임의 URL 프록시 기능을 제공하지
                                                  않는다.

  SEC-10                  감사 가능성             관리자 정책 변경, 배포, 권한
                                                  변경, 라우팅 변경, 장애 조치
                                                  이력을 위변조 방지 가능한 내부
                                                  로그로 남긴다.

  SEC-11                  장애 안전성             대기열 장애 시 통과/차단 동작은
                                                  사이트별 Fail-open 또는
                                                  Fail-closed 정책으로 명시한다.

  SEC-12                  검증 가능한 배포        설정은 버전·서명·스키마 검증 후
                                                  적용하고, 실패 시 이전 정상
                                                  버전으로 복구할 수 있어야 한다.
  -------------------------------------------------------------------------------

### 2.2 보안 우선순위

1.  내부망 및 DB Zone 보호
2.  DMZ 침해 시 내부 확산 차단
3.  내부 API와 원본 서비스의 직접 노출 방지
4.  입장 토큰 위조·재사용 방지
5.  대기열 상태의 무결성과 동시 입장 제한
6.  운영 편의성 및 동적 설정

운영 편의를 위해 내부망 접근 경계를 완화해서는 안 된다.

## 3. 네트워크 Zone 및 배치 아키텍처

### 3.1 논리 Zone

  -----------------------------------------------------------------------
  Zone                    주요 구성요소           보안 목적
  ----------------------- ----------------------- -----------------------
  Internet                사용자 브라우저, 외부   신뢰할 수 없는 외부
                          클라이언트              네트워크

  DMZ                     외부 진입 Gateway,      외부 요청 수용 및
                          Queue Engine, Waiting   대기열 처리
                          Room, Queue Redis       

  내부 관리망             Admin UI, Control API,  운영자 인증 및 정책
                          정책 배포 서비스,       관리
                          모니터링 관리           

  DB Zone                 MariaDB, 내부 백업 및   정책·계정·감사 데이터
                          감사 데이터 저장소      보호

  내부 서비스망           Internal API Ingress,   업무 서비스 보호
                          비즈니스 API, 원본 웹   
                          서비스                  
  -----------------------------------------------------------------------

### 3.2 권장 논리 구성도

``` mermaid
flowchart TB
    U[외부 사용자 브라우저]
    subgraph DMZ[DMZ - 외부 요청 처리 Zone]
        LB[외부 Load Balancer / WAF]
        GW[Edge Gateway]
        QE[Queue Engine]
        WR[Waiting Room Web]
        QR[(Queue Redis)]
        LB --> GW
        GW --> QE
        GW --> WR
        QE <--> QR
    end

    subgraph MGMT[내부 관리망 - Control Plane]
        ADMIN[Admin Dashboard]
        API[Control API]
        CFG[정책 서명 및 배포 서비스]
        MON[Monitoring / Alerting]
        ADMIN --> API
        API --> CFG
    end

    subgraph DBZ[DB Zone]
        MDB[(MariaDB)]
        AUD[(Audit Log Storage)]
    end

    subgraph SVC[내부 서비스망]
        IING[Internal API Ingress]
        BIZ[Business APIs / Origin Services]
        IING --> BIZ
    end

    U --> LB
    API <--> MDB
    API --> AUD
    CFG -. 승인된 서명 정책 배포 .-> QE
    GW -->|허용된 HTTPS + mTLS| IING
```

> 구성도는 논리 흐름을 나타낸다. 실제 방화벽 정책, 단방향 전송 여부,
> 라우팅 경로 및 운영망 연결은 기관 네트워크 정책에 따라 확정해야 한다.

### 3.3 구성요소 배치 기준

  -------------------------------------------------------------------------
  구성요소     배치             내부 DB 접근 외부 인터넷 접근 비고
  ------------ ------------ ---------------- ---------------- -------------
  외부 Load    DMZ 경계                 불가             허용 공개 진입점
  Balancer /                                                  
  WAF                                                         

  Edge Gateway DMZ                      불가   외부 요청 수신 비즈니스 DB
                                                              계정 금지

  Queue Engine DMZ                      불가     Gateway 경유 Redis만
                                                              필요한 범위로
                                                              접근

  Waiting Room DMZ                      불가             허용 정적 리소스
  Web                                                         중심

  Queue Redis  DMZ 전용                 불가   직접 공개 금지 Queue Engine
               서브넷                                         전용

  Admin        내부 관리망    직접 접속 지양      원칙적 차단 VPN/Bastion
  Dashboard                                                   등 승인 경로

  Control API  내부 관리망    승인된 DB 접속      원칙적 차단 정책·운영
                                                              제어

  MariaDB      DB Zone             해당 없음             차단 Control API
                                                              등 승인된
                                                              주체만 접근

  Audit 저장소 DB Zone 또는        해당 없음             차단 DMZ가 직접
               내부 로그                                      삭제·수정할
               Zone                                           수 없도록
                                                              구성

  Internal API 내부             업무 DB 직접             차단 DMZ Gateway의
  Ingress      서비스망            접근 지양                  허용된 요청만
               경계                                           수신

  Business API 내부           업무 DB 정책에             차단 인터넷에서
  / Origin     서비스망                 따름                  직접 접근
                                                              불가
  -------------------------------------------------------------------------

## 4. Zone 간 네트워크 보안 정책

### 4.1 기본 방화벽 정책

아래 표는 논리적인 기본 정책이다. 실제 포트 번호는 배포 환경과 제품
선택에 따라 확정한다.

  ----------------------------------------------------------------------------------
  출발지         목적지         허용 여부      통신 목적          필수 통제
  -------------- -------------- -------------- ------------------ ------------------
  Internet       DMZ 외부       허용           HTTPS 사용자 접속  WAF, DDoS 방어,
                 LB/WAF                                           TLS

  Internet       DMZ Gateway    차단           해당 없음          관리 포트 비노출
                 직접 관리 포트                                   

  Internet       Queue Redis    차단           해당 없음          사설 인터페이스만
                                                                  바인딩

  Internet       내부 관리망/DB 차단           해당 없음          경계 방화벽 기본
                 Zone/내부                                        차단
                 서비스망                                         

  DMZ Gateway    Queue Engine   제한 허용      내부 서비스 호출   전용 포트, 서비스
                                                                  인증

  Queue Engine   Queue Redis    허용           대기열 상태 처리   TLS, ACL, 전용
                                                                  계정

  DMZ Gateway    Internal API   제한 허용      승인된 업무 요청   목적지 allowlist,
                 Ingress                       전달               mTLS, API 제한

  Queue Engine   MariaDB        차단           해당 없음          DB 라우팅 및 계정
                                                                  미보유

  DMZ 전체       DB Zone        차단           해당 없음          양방향 차단

  DMZ            내부 관리망    기본 차단      정책 수신 등 예외  승인된 배포 채널만
                                               필요 시            허용

  내부 관리망    MariaDB        허용           정책·사이트·운영   DB 방화벽, TLS,
  Control API                                  데이터             최소 권한

  내부 관리망    DMZ 배포 수신  제한 허용      서명된 정책 배포   고정
  배포 서비스    지점                                             목적지·인증·서명
                                                                  검증

  DMZ            내부 관리망    기본 차단      로그 전달 예외     전용 수집 경로
                 로그 수집                                        또는 내부 Pull

  내부 모니터링  DMZ Metrics    제한 허용 또는 상태·지표 수집     인증, IP 제한,
                 Endpoint       DMZ outbound                      읽기 전용

  Internal API   Business API   허용           업무 요청 전달     내부 인증 및 접근
  Ingress                                                         제어

  Internet       Business API / 차단           직접 접속 방지     Ingress/LB 경유
                 Origin                                           강제
  ----------------------------------------------------------------------------------

**중요:** "내부에서 DMZ 방향 단방향 배포"는 네트워크 장비의 실제 단방향
전송 기능을 의미하는지, 내부에서 시작한 연결을 통해 DMZ 수신기가
데이터를 받는 구조인지 구분해야 한다. TCP 세션 기반의 일반적인 Push는
물리적 단방향 통신과 같지 않다. 보안팀의 Zone 정책에 맞춰 파일 중계,
Pull 방식, 단방향 게이트웨이 또는 승인된 mTLS 배포 채널 중 하나를
선택한다.

### 4.2 DMZ 침해 가정과 확산 방지

DMZ의 Gateway 또는 Queue Engine이 공격자에게 장악되더라도 다음 행위가
불가능해야 한다.

-   내부 MariaDB에 접속하거나 데이터를 조회하는 행위
-   내부 관리자 계정·비밀번호·세션을 획득하는 행위
-   임의의 내부 IP/포트로 접근하는 행위
-   임의 URL을 지정하여 내부 자산을 프록시하는 행위
-   내부 API의 모든 기능을 무제한 호출하는 행위
-   내부 로그·감사 기록을 삭제하거나 수정하는 행위
-   토큰 서명키를 탈취하여 임의 입장 토큰을 생성하는 행위

이를 위해 DMZ에는 내부 DB 접속정보, 관리자 비밀정보, 업무 서비스
자격증명 및 장기 서명 개인키를 두지 않는 것을 기본 원칙으로 한다.

## 5. Control Plane / Data Plane 설계

### 5.1 Control Plane --- 내부 관리망

Control Plane은 운영자가 정책을 정의하고 관리하는 신뢰 영역이다.

주요 책임:

-   사이트 및 서비스 등록
-   대기열 정책 설정
-   백엔드 대상 등록 및 승인
-   라우팅 정책 생성·검증·배포
-   Gateway/Queue Engine 노드 등록 및 상태 관리
-   정책 버전 관리, 승인, 롤백
-   관리자 인증·인가 및 감사 로그
-   운영 통계와 장애 이력 관리

Control Plane의 영속 데이터는 DB Zone의 MariaDB에 저장한다. Control
API만 필요한 DB 계정과 네트워크 경로를 가진다.

### 5.2 Data Plane --- DMZ

Data Plane은 외부 요청을 처리하고 실제 대기열을 운영한다.

주요 책임:

-   사용자 요청 수신
-   사이트·경로별 대기열 정책 적용
-   대기열 등록 및 순번 관리
-   동시 입장 가능 인원 제어
-   대기 상태 제공
-   입장 토큰 발급 또는 승인된 발급 서비스 연계
-   입장 토큰 검증 보조 및 재사용 상태 관리
-   승인된 백엔드로 요청 전달
-   런타임 정책 스냅샷 적용

Data Plane은 내부 관리 DB에 직접 접근하지 않는다. DMZ Redis는 대기열
상태만 보관하며 Control Plane의 영속 데이터 저장소로 사용하지 않는다.

### 5.3 정책 배포 흐름

1.  운영자가 내부 Admin Dashboard에서 정책을 생성한다.
2.  Control API가 정책의 스키마, 권한, 대상 서비스, 목적지 allowlist,
    충돌 여부를 검증한다.
3.  승인된 정책을 불변(immutable) 버전 스냅샷으로 생성한다.
4.  정책 배포 서비스가 스냅샷에 서명한다.
5.  승인된 네트워크 경로로 DMZ 배포 수신 지점에 전달한다.
6.  DMZ 노드는 서명, 발행자, 버전, 만료/유효기간, 스키마 및 대상 허용
    목록을 검증한다.
7.  검증에 성공하면 메모리의 런타임 설정을 원자적으로 교체한다.
8.  실패하면 현재 적용 중인 정상 버전을 유지하고 오류를 기록한다.
9.  노드 적용 결과는 보안 정책이 허용하는 승인된 상태 보고 채널로
    수집한다.

정책 배포가 실패하거나 일부 노드에만 적용된 경우, Control Plane은 노드별
적용 버전과 상태를 구분해 표시해야 한다.

### 5.4 토큰 서명 및 검증 신뢰 모델

입장 토큰은 내부 API 접근을 허가하는 최종 권한 증명으로 취급해서는 안
된다. 토큰은 대기열 통과 사실과 제한된 권한을 증명하며, 내부 Ingress와
비즈니스 서비스가 최종 접근 제어를 수행한다.

권장 방향:

-   HMAC처럼 발급·검증 주체가 동일 비밀키를 공유하는 방식은 DMZ 침해 시
    내부 검증 체계까지 영향을 줄 수 있으므로 기본 선택으로 삼지 않는다.
-   Ed25519 또는 ECDSA/RSA와 같은 비대칭 서명을 우선 검토한다.
-   개인키는 내부 KMS/HSM 또는 보호된 서명 서비스에 보관하는 방안을 우선
    검토한다.
-   DMZ에는 검증용 공개키만 배포하는 것이 바람직하다.
-   다만 실시간 토큰 발급을 위해 DMZ가 서명 요청을 해야 한다면, 서명
    서비스는 mTLS, 엄격한 claim 정책, 짧은 토큰 수명, 발급량 제한,
    사이트·큐 범위 제한 및 감사 기록을 강제해야 한다.
-   DMZ가 개인키를 보유하는 설계는 DMZ 침해 시 위조 토큰 위험이 있으므로
    보안 승인 없이는 채택하지 않는다.

토큰에 포함할 claim 후보:

  Claim                 목적
  --------------------- -------------------------------------
  `iss`                 발급자 식별
  `aud`                 허용된 대상 서비스 식별
  `sub`                 사용자/세션 식별자(개인정보 최소화)
  `site_id`             사이트 식별
  `queue_id`            대기열 식별
  `jti`                 토큰 고유 ID 및 재사용 방지
  `iat`, `nbf`, `exp`   발급·사용 가능·만료 시각
  `scope`               허용된 제한 권한
  `policy_version`      적용 정책 버전
  `session_binding`     세션/쿠키와의 제한적 바인딩 값

내부 Ingress는 서명, 발급자, audience, 만료, 허용 scope, 사이트/큐 일치
여부를 검증해야 한다. 일회성 토큰 또는 재사용 방지가 필요한 경우 `jti`
사용 여부를 내부 검증 상태 저장소에서 확인한다. 토큰에 민감한 개인정보를
넣지 않는다.

## 6. 동적 라우팅 설계

### 6.1 운영 요구사항

-   백엔드 서버 추가·삭제·변경 시 Gateway 재기동 없이 반영
-   여러 Gateway 노드에 동일 정책 배포
-   정책 변경 이력과 승인자 기록
-   설정 오류 시 이전 정상 버전으로 롤백
-   노드별 적용 버전 및 적용 실패 상태 확인
-   사이트·경로·HTTP method·우선순위에 따른 라우팅
-   사이트별 대기열 활성화/비활성화 및 정책 연결

### 6.2 권장 구현

Spring Cloud Gateway를 사용할 경우 YAML은 서버 구동에 필요한 기본 설정만
담당하고, 운영 라우트는 Control Plane에서 관리하는 정책 스냅샷을
기반으로 런타임에 구성한다.

구현 후보:

-   `RouteDefinitionRepository` 또는 사용자 정의
    `RouteDefinitionLocator`
-   런타임 라우트 테이블 및 원자적 교체
-   라우트 변경 시 명시적인 refresh 이벤트
-   다중 Gateway 노드에는 동일한 서명 정책 버전을 배포
-   노드는 자체 검증 후 적용하고, 정책 버전과 오류를 기록

단순히 DB 기반 RouteDefinitionRepository를 추가하는 것만으로 다중
Gateway 동기화, 보안 검증, 롤백이 자동 해결되지는 않는다. 서명된 버전
스냅샷과 노드별 적용 상태 관리가 별도로 필요하다.

### 6.3 SSRF 및 내부망 접근 방지

라우팅 대상은 운영자가 임의 URL을 입력하는 자유 프록시 방식으로 구현하지
않는다.

필수 통제:

1.  내부 Control Plane에서 백엔드 대상 호스트·포트·프로토콜을 사전
    등록한다.
2.  승인된 서비스 ID를 라우트에 참조하도록 한다.
3.  호스트명뿐 아니라 DNS 해석 후 IP도 정책에 맞는지 확인한다.
4.  loopback, link-local, metadata endpoint, 미승인 private subnet 등
    금지 대상을 차단한다.
5.  DNS rebinding 및 검증 후 목적지 변경 가능성을 고려한다.
6.  HTTP method, path prefix, header 전달 범위를 제한한다.
7.  Redirect를 따라갈 때도 최종 목적지를 재검증한다.
8.  Gateway egress 방화벽에서도 허용 목적지 외 통신을 차단한다.

## 7. 대기열 Redis 설계

### 7.1 배치와 역할

-   Redis는 DMZ의 Queue 전용 서브넷에 배치한다.
-   인터넷에서 Redis 포트에 직접 접근할 수 없어야 한다.
-   Queue Engine만 Redis에 접속하도록 방화벽과 Redis ACL을 함께
    적용한다.
-   내부 MariaDB와 복제·동기화하지 않는다.
-   Redis에는 대기열 운영에 필요한 최소 상태만 저장한다.

저장 후보:

-   대기열 순서 및 진입 시각
-   대기 세션 식별자
-   입장 허가/lease 상태
-   heartbeat 및 TTL
-   토큰 `jti` 재사용 방지 상태(필요한 경우)
-   사이트/큐별 현재 입장 카운터
-   임시 rate-limit 카운터

### 7.2 Redis 보안 기준

-   TLS 적용
-   ACL 사용자별 최소 명령 및 key pattern 권한
-   기본 사용자 비활성화 또는 강력한 별도 통제
-   사설 인터페이스 bind 및 방화벽 제한
-   관리 명령의 애플리케이션 계정 사용 금지
-   운영자 접근은 별도 관리 경로로 제한
-   비밀번호/인증정보는 Secret Manager 등으로 관리
-   Redis 데이터의 보존 기간과 개인정보 포함 여부 점검
-   백업·복구 및 장애 복구 목표(RPO/RTO) 정의
-   Sentinel 또는 Redis Cluster 여부는 목표 가용성, 데이터 손실 허용치,
    운영 복잡성을 기준으로 선택

### 7.3 동시 입장 제어

단순 FIFO 순번만으로는 동시 입장 상한을 보장할 수 없다.

-   Redis Lua Script 또는 원자적 자료구조 연산으로 입장 인원 할당과
    카운터 갱신을 한 트랜잭션처럼 처리한다.
-   입장 권한은 TTL이 있는 lease로 관리한다.
-   브라우저 heartbeat 또는 주기적 갱신으로 활성 세션을 판단한다.
-   브라우저 종료·네트워크 단절 시 lease 만료 후 자원을 회수한다.
-   중복 요청과 재시도에도 같은 세션이 중복 입장권을 얻지 않도록
    idempotency key를 적용한다.
-   장애 조치 시 카운터가 실제보다 작아져 초과 입장이 발생하지 않도록
    복구 정책을 정의한다.

## 8. 사용자 요청 및 입장 흐름

### 8.1 기본 흐름

1.  사용자가 보호 대상 웹사이트 URL에 접속한다.
2.  외부 WAF/LB가 요청을 DMZ Gateway로 전달한다.
3.  Gateway가 사이트 및 경로 정책을 조회한다.
4.  대기열이 비활성화되어 있거나 입장 허용 상태이면 승인된 라우트로
    전달한다.
5.  대기열이 활성화되어 있으면 Queue Engine이 Redis에 대기 세션을
    등록한다.
6.  Waiting Room이 순번, 예상 대기시간, 상태를 제공한다.
7.  Queue Engine은 동시 입장 한도와 FIFO 정책에 따라 입장 가능 세션을
    선정한다.
8.  필요한 경우 제한된 수명의 서명 입장 토큰을 발급한다.
9.  브라우저는 허용된 경로로 원래 서비스를 재요청한다.
10. Internal API Ingress가 토큰과 요청 범위를 검증한 후 비즈니스 API로
    전달한다.

### 8.2 대기 상태 제공

-   실시간 또는 준실시간 순번 갱신
-   예상 대기시간(추정치임을 명시)
-   대기열 혼잡도 및 입장 속도 안내
-   브라우저 새로고침/재접속 시 기존 세션 복구
-   세션 만료 및 재진입 정책
-   다중 탭 및 중복 세션 처리
-   접근성 및 모바일 반응형 화면

SSE(Server-Sent Events)를 기본 후보로 검토한다. 양방향 실시간 통신이
반드시 필요한 경우에만 WebSocket을 추가 검토한다.

## 9. 논리 기능 모듈 및 기능 목록

### 9.1 모듈 구성 요약

  ---------------------------------------------------------------------------
  모듈 ID           모듈              주요 배치         주요 책임
  ----------------- ----------------- ----------------- ---------------------
  M01               Edge Gateway      DMZ               외부 요청 수신, TLS
                                                        종료 연계, 라우팅,
                                                        제한·보호

  M02               Queue Engine      DMZ               대기열 상태 및 입장
                                                        제어

  M03               Waiting Room UI   DMZ               사용자 대기 화면 및
                                                        상태 갱신

  M04               Queue Redis       DMZ               Redis 데이터 접근,
                    Adapter                             원자 연산

  M05               Token Service     DMZ/보호된 서명   입장 토큰 발급·검증
                    Adapter           서비스 연계       연계

  M06               Dynamic Routing   DMZ               검증된 정책 기반
                                                        런타임 라우팅

  M07               Policy            내부 관리망 및    서명 정책
                    Distribution      DMZ 수신부        생성·배포·검증·적용

  M08               Admin Dashboard   내부 관리망       운영자 UI

  M09               Control API       내부 관리망       관리 기능 API 및 정책
                                                        검증

  M10               Configuration &   내부 관리망/DB    사이트·서비스·정책
                    Site Registry     Zone              영속 관리

  M11               Security & IAM    내부 관리망       인증, RBAC, MFA 연계,
                                                        비밀정보 관리

  M12               Audit &           내부              감사, 지표, 알림,
                    Monitoring        관리망/DB·로그    운영 상태
                                      Zone              

  M13               Internal API      내부 서비스망     토큰 검증 및 원본
                    Ingress                             서비스 보호
                    Integration                         

  M14               Operations &      각 Zone           설치, 업그레이드,
                    Deployment                          백업, 복구, 헬스체크
  ---------------------------------------------------------------------------

### 9.2 M01 --- Edge Gateway

-   HTTP/HTTPS 요청 수신 및 전달
-   Host, path, method 기반 사이트 식별
-   동적 라우팅 정책 적용
-   요청 크기·속도·헤더 제한
-   기본 rate limiting 및 비정상 요청 차단 연계
-   신뢰할 수 있는 Proxy 헤더 처리
-   클라이언트 IP 위조 방지
-   TLS 정책 및 보안 헤더 적용
-   대기열 진입·통과 상태에 따른 요청 분기
-   백엔드 연결 실패 및 timeout 처리
-   요청 ID 및 상관관계 ID 부여
-   허용 목적지 외 egress 차단

### 9.3 M02 --- Queue Engine

-   사이트/큐별 대기열 생성 및 관리
-   FIFO 대기 순서 관리
-   동시 입장 수 제한
-   초당/분당 입장률 제한
-   대기 세션 등록·갱신·만료
-   중복 등록 및 재시도 멱등성
-   heartbeat 및 lease 관리
-   대기 순번 조회
-   예상 대기시간 계산
-   입장 가능 세션 선정
-   세션 취소 및 이탈 처리
-   재접속 및 세션 복구
-   사이트별 Fail-open/Fail-closed 정책 적용
-   Redis 장애 및 Queue Engine 장애 처리
-   멀티 노드 동시성 제어

### 9.4 M03 --- Waiting Room UI

-   사이트별 대기 화면
-   현재 순번 표시
-   예상 대기시간 표시
-   대기 상태 자동 갱신(SSE 우선)
-   새로고침 및 재접속 시 상태 복구
-   대기 취소 및 만료 안내
-   공지·브랜드·테마 설정
-   모바일 및 데스크톱 반응형
-   접근성 지원
-   다국어 확장 고려
-   비정상 클라이언트 요청 완화

### 9.5 M04 --- Queue Redis Adapter

-   Redis 연결 및 TLS
-   ACL 기반 인증
-   key namespace 분리
-   Lua 기반 원자 연산
-   대기열 자료구조 관리
-   TTL/lease 관리
-   Redis Cluster/Sentinel 연결 옵션
-   재시도 및 circuit breaker
-   Redis 연결·명령 지표
-   데이터 만료 및 정리

### 9.6 M05 --- Token Service Adapter

-   입장 토큰 발급 서비스 연계
-   비대칭 서명 검증
-   공개키 및 key ID 관리
-   짧은 만료시간 적용
-   `iss`, `aud`, `exp`, `jti`, `site_id`, `queue_id`, `scope` 검증
-   토큰 재사용 방지 연계
-   세션 바인딩 검토
-   키 교체 및 폐기
-   토큰 발급·검증 감사 이벤트
-   토큰에 민감정보 저장 금지

### 9.7 M06 --- Dynamic Routing

-   사이트별 도메인 및 경로 매핑
-   백엔드 서비스 ID 기반 목적지 선택
-   HTTP method/path 조건
-   우선순위 및 경로 충돌 검증
-   런타임 라우트 갱신
-   다중 Gateway 정책 일치
-   라우트 버전 표시
-   승인된 목적지 allowlist
-   SSRF 방지 검증
-   헬스체크 결과 기반 라우팅 정책 연계
-   롤백 및 마지막 정상 설정 유지

### 9.8 M07 --- Policy Distribution

-   정책 스키마 정의 및 버전 관리
-   정책 스냅샷 생성
-   정책 서명
-   배포 대상 노드 관리
-   정책 전달 및 수신
-   서명·발행자·버전 검증
-   schema validation
-   목적지 allowlist 검증
-   원자적 정책 적용
-   노드별 적용 결과 및 오류 관리
-   이전 버전 롤백
-   정책 만료·폐기 처리
-   배포 승인 워크플로

### 9.9 M08 --- Admin Dashboard

-   관리자 로그인
-   사이트/서비스 목록
-   대기열 정책 관리
-   백엔드 대상 관리
-   라우팅 정책 관리
-   Gateway/Queue 노드 상태
-   실시간 대기 인원 및 처리량
-   입장률 및 동시 접속 현황
-   정책 배포 및 버전 비교
-   롤백
-   운영 공지 및 화면 테마 관리
-   감사 로그 조회
-   장애 및 경보 조회

### 9.10 M09 --- Control API

-   사이트 CRUD
-   서비스/백엔드 등록 및 검증
-   대기열 정책 CRUD
-   라우팅 정책 CRUD
-   정책 승인·배포·롤백
-   노드 등록 및 상태 조회
-   관리자 권한 검증
-   감사 이벤트 생성
-   운영 통계 API
-   입력값 및 목적지 보안 검증
-   API rate limit 및 idempotency

### 9.11 M10 --- Configuration & Site Registry

-   사이트 도메인 및 식별자
-   서비스 및 백엔드 등록정보
-   라우트 정의 및 버전
-   대기열 정책
-   사이트별 장애 정책
-   정책 배포 이력
-   노드 인벤토리
-   운영자 및 권한 참조
-   설정 변경 이력
-   DB 스키마 버전 관리

### 9.12 M11 --- Security & IAM

-   관리자 인증
-   OIDC/SAML 연계 검토
-   MFA 적용
-   RBAC 및 역할 관리
-   관리자 세션 관리
-   서비스 간 mTLS 인증
-   비밀정보 관리 연계
-   서명키/KMS/HSM 연계
-   계정 잠금 및 접근 이력
-   권한 변경 감사
-   보안 설정 점검

### 9.13 M12 --- Audit & Monitoring

-   Gateway 요청·오류 지표
-   Queue 길이 및 대기시간 지표
-   입장률 및 동시 접속 지표
-   Redis 상태 및 지연
-   정책 버전 및 배포 상태
-   노드 헬스체크
-   토큰 검증 실패 지표
-   비정상 트래픽 및 rate-limit 지표
-   관리자 작업 감사
-   경보 및 알림
-   로그 보존·접근통제
-   DMZ 로그의 내부 수집 및 무결성 보호

### 9.14 M13 --- Internal API Ingress Integration

-   DMZ Gateway의 허용된 요청만 수신
-   mTLS 클라이언트 인증
-   입장 토큰 서명 및 claim 검증
-   audience 및 서비스 범위 검증
-   토큰 만료 및 재사용 검증
-   허용 HTTP method/path 제한
-   사용자 세션 및 업무 권한 재검증
-   원본 서비스 직접 노출 차단
-   요청 크기·속도 제한
-   추적 ID 전달 및 감사

### 9.15 M14 --- Operations & Deployment

-   Zone별 설치 패키지
-   환경별 설정 분리
-   Secret 외부 주입
-   서비스 계정 및 파일 권한 설정
-   헬스체크 및 readiness/liveness
-   무중단 또는 단계적 업그레이드
-   DB migration
-   Redis 백업 및 복구
-   정책 백업 및 복원
-   로그 로테이션
-   장애 복구 절차
-   설치 전 사전 점검 및 설치 후 보안 점검
-   구성 drift 감지

## 10. 권장 오픈소스 패키지 및 기술 스택

아래는 구현 후보이며 최종 버전은 호환성 검증 및 라이선스 확인 후
고정한다.

### 10.1 Backend 및 Gateway

  -------------------------------------------------------------------------
  영역              후보                용도              선정 메모
  ----------------- ------------------- ----------------- -----------------
  개발 언어         Java 21 LTS         Backend           장기 지원 런타임
                                                          후보

  Backend Framework Spring Boot 3.5.x   Control API,      안정적인 Java
                                        Queue Engine      생태계

  API Gateway       Spring Cloud        Edge Gateway      동적 라우팅 및
                    Gateway                               필터

  Security          Spring Security     인증·인가         RBAC/OIDC 연계

  API 문서          springdoc-openapi   OpenAPI 문서      API 계약 관리

  DB 접근           Spring Data JPA     MariaDB 접근      SQL은 Java 코드와
                    또는 jOOQ                             분리 관리

  DB Migration      Flyway              스키마 버전 관리  SQL migration
                                                          파일 별도 관리

  HTTP Client       Spring WebClient    내부 서비스 호출  timeout/TLS 정책

  Resilience        Resilience4j        timeout, retry,   재시도 폭주 방지
                                        circuit breaker   
  -------------------------------------------------------------------------

### 10.2 Queue 및 Cache

  -----------------------------------------------------------------------
  영역              후보              용도              선정 메모
  ----------------- ----------------- ----------------- -----------------
  Queue State       Redis 7.x 계열    대기열 상태       DMZ Queue 전용

  Java Redis Client Lettuce           Redis 연결        Spring Data
                                                        Redis와 연계 가능

  Atomic Operation  Redis Lua Script  원자적 입장 제어  동시 입장 상한
                                                        보장

  Queue Data        Sorted Set /      순번·상태 관리    실제 부하 테스트
  Structure         Stream / Hash                       후 선택

  HA                Redis Sentinel    Redis 가용성      규모·RPO/RTO에
                    또는 Redis                          따라 선택
                    Cluster                             
  -----------------------------------------------------------------------

Redis를 메시지 브로커, 내부 정책 DB 또는 영구 감사 저장소로 과도하게
사용하지 않는다. 대기열 상태와 운영 정책의 영속 저장소를 구분한다.

### 10.3 Frontend

  영역           후보                  용도
  -------------- --------------------- --------------------------
  Framework      React                 Admin 및 Waiting Room UI
  Language       TypeScript            프론트엔드 타입 안정성
  Build          Vite                  빌드 및 개발 서버
  UI             MUI 또는 Ant Design   관리자 화면
  Server State   TanStack Query        API 상태 관리
  Routing        React Router          화면 라우팅
  Realtime       EventSource(SSE)      대기 상태 갱신

### 10.4 Database 및 인증

  -----------------------------------------------------------------------------
  영역              후보              용도                    보안 메모
  ----------------- ----------------- ----------------------- -----------------
  RDBMS             MariaDB           내부                    DB Zone 배치
                                      정책·사이트·관리·감사   
                                      메타데이터              

  Migration         Flyway            DB schema 관리          SQL 파일 별도
                                                              관리

  Identity Provider Keycloak          관리자 OIDC 및 SSO      내부 관리망 배치

  Secrets           HashiCorp Vault   비밀정보 관리           DMZ에 장기
                    또는 조직 표준                            비밀정보 저장
                    Secret Manager                            금지

  Key Management    조직 KMS/HSM      서명키 보호             비대칭 서명 우선
  -----------------------------------------------------------------------------

### 10.5 Observability 및 운영

  영역            후보                                    용도
  --------------- --------------------------------------- -----------------------
  Metrics         Prometheus                              서비스·Redis 지표
  Dashboard       Grafana                                 운영 대시보드
  Logs            OpenSearch 또는 조직 표준 로그 플랫폼   중앙 로그 검색
  Tracing         OpenTelemetry                           분산 추적
  Alerting        Alertmanager                            경보 전달
  Container       Docker/Podman                           컨테이너 패키징
  Orchestration   Kubernetes 또는 systemd                 운영 환경에 맞춰 선택
  Edge WAF        ModSecurity + OWASP CRS 또는 기존 WAF   웹 공격 방어
  Load Balancer   HAProxy 또는 조직 표준 LB               외부/내부 트래픽 분산

### 10.6 라이선스 및 공급망 확인

오픈소스 패키지를 선정할 때 다음을 확인한다.

-   프로젝트별 라이선스 및 상용 배포 조건
-   의존성 취약점(CVE)
-   지원 중인 버전과 보안 업데이트 상태
-   컨테이너 이미지 출처 및 서명
-   SBOM 생성 및 보관
-   빌드 재현성 및 artifact provenance
-   라이선스 고지 및 NOTICE 파일
-   폐쇄망 설치를 위한 패키지/이미지 미러링

특정 버전 번호를 문서에 고정하기 전 공식 릴리스 노트와 상호 호환성을
확인한다.

## 11. 데이터 저장소 분리 원칙

  -----------------------------------------------------------------------
  데이터                  저장 위치               예시
  ----------------------- ----------------------- -----------------------
  사이트·서비스           내부 DB Zone MariaDB    site, backend_service
  메타데이터                                      

  대기열 정책             내부 DB Zone MariaDB    queue_policy

  라우팅 정책             내부 DB Zone MariaDB    route_policy

  정책 버전 및 배포 이력  내부 DB Zone MariaDB    policy_version,
                                                  deployment

  관리자 및 권한          내부 DB Zone 또는 IAM   admin, role
  메타데이터                                      

  감사 이력               내부 로그/DB Zone       audit_event

  실시간 대기 순서        DMZ Redis               queue entry

  입장 lease 및 카운터    DMZ Redis               admission lease

  임시 토큰 재사용 상태   정책에 따라 DMZ Redis   jti
                          또는 내부 검증 저장소   

  비즈니스 데이터         기존 업무 DB            OpenQueue가 직접
                                                  소유하지 않음
  -----------------------------------------------------------------------

OpenQueue는 원본 업무 데이터와 사용자 개인정보를 불필요하게 복제하지
않는다.

## 12. 가용성·확장성·장애 정책

### 12.1 확장 전략

-   Gateway는 가능한 한 stateless로 구성하고 수평 확장한다.
-   Queue Engine은 Redis를 공유하되 원자 연산으로 경쟁 상태를 방지한다.
-   Waiting Room 정적 자원은 캐시/CDN 적용을 검토한다.
-   사이트별 대기열 키 공간을 분리한다.
-   Redis 병목을 고려해 사이트/큐별 부하와 key hot spot을 측정한다.
-   Control Plane 장애가 Data Plane의 이미 적용된 정책 실행을 즉시
    중단시키지 않도록 한다.

### 12.2 장애 동작

  -----------------------------------------------------------------------
  장애                    권장 기본 동작          추가 결정사항
  ----------------------- ----------------------- -----------------------
  Control API 장애        마지막 정상 정책으로    정책 변경 불가 상태
                          운영 지속               표시

  정책 배포 실패          마지막 정상 정책 유지   노드별 오류 경보

  Redis 장애              사이트 정책에 따라      업무 중요도별 예외
                          Fail-closed 우선 검토   

  Queue Engine 일부 장애  정상 노드로 분산        세션 복구 및 중복 입장
                                                  방지

  Gateway 장애            LB가 정상 노드로 우회   모든 노드 장애 시 정책

  서명 서비스 장애        신규 입장 토큰 발급     기존 토큰 검증은
                          제한                    공개키로 지속 가능

  내부 API Ingress 장애   업무 서비스 보호를 위해 업무 연속성 기준
                          요청 차단 또는 대체     
                          경로                    
  -----------------------------------------------------------------------

Fail-open은 서비스 접근성을 높일 수 있지만 폭주 방어가 무력화될 수 있다.
Fail-closed는 보호 수준을 높이나 업무 서비스가 차단될 수 있다.
사이트별로 승인된 정책을 명시한다.

## 13. 운영 및 관리자 기능

### 13.1 사이트 관리

-   사이트 등록/수정/비활성화
-   도메인 및 경로 매핑
-   사이트별 Gateway 정책
-   사이트별 기본 대기열
-   사이트별 운영 시간 및 공지
-   사이트별 장애 정책

### 13.2 백엔드 서비스 관리

-   서비스 ID 및 설명
-   대상 호스트/포트/프로토콜 등록
-   허용 IP 대역 및 DNS 검증
-   헬스체크 URL 및 주기
-   서비스 활성/비활성
-   라우트 연결
-   변경 승인 및 이력

### 13.3 대기열 정책 관리

-   FIFO/우선순위 정책
-   동시 입장 상한
-   초당/분당 입장률
-   대기 세션 TTL
-   heartbeat 주기
-   재진입 정책
-   최대 대기시간
-   대기열 활성/비활성
-   Fail-open/Fail-closed
-   사용자 화면 공지

### 13.4 운영 모니터링

-   현재 대기자 수
-   입장 대기자 수
-   활성 입장 세션 수
-   평균/백분위 대기시간
-   초당 입장 수
-   Gateway 요청량 및 오류율
-   Redis 지연·메모리·연결 수
-   노드별 정책 적용 버전
-   토큰 검증 실패율
-   비정상 트래픽 및 차단 현황

## 14. 필수 보안 테스트 및 검증 항목

### 14.1 네트워크 분리 테스트

-   Internet에서 Redis/관리 UI/Control API/DB 접근 불가 확인
-   DMZ에서 MariaDB 연결 불가 확인
-   DMZ에서 임의 내부 IP/포트 접근 불가 확인
-   Gateway에서 등록되지 않은 백엔드로 연결 불가 확인
-   Origin 서버가 인터넷에서 직접 접근되지 않는지 확인
-   내부 관리망에서 DMZ로 허용된 배포 포트 외 접근 차단 확인

### 14.2 애플리케이션 보안 테스트

-   SSRF 및 DNS rebinding 테스트
-   Host 헤더 및 Forwarded 헤더 위조 테스트
-   JWT/서명 토큰 위조·만료·audience 변조 테스트
-   토큰 재사용 및 다른 사이트/큐 간 재사용 테스트
-   권한 없는 관리자 API 호출 테스트
-   CSRF/XSS/Injection 테스트
-   대기열 등록 API 자동화 공격 및 rate limit 테스트
-   다중 탭/동시 요청/재시도 경합 테스트
-   Redis ACL 우회 및 명령 제한 테스트

### 14.3 부하 및 복구 테스트

-   동시 접속 급증 테스트
-   장시간 대기 세션 테스트
-   Redis 재시작 및 failover 테스트
-   Queue Engine 노드 중단 테스트
-   Gateway 노드 중단 테스트
-   정책 배포 도중 노드 재시작 테스트
-   잘못된 정책 서명 및 구버전 정책 거부 테스트
-   롤백 및 부분 배포 복구 테스트
-   백업 복구 및 RPO/RTO 측정

## 15. 구현 단계 제안

  -----------------------------------------------------------------------
  단계                    범위                    완료 기준
  ----------------------- ----------------------- -----------------------
  Phase 0                 요구사항·위협           Zone 통신표, 토큰 신뢰
                          모델·네트워크 정책 확정 모델, 장애 정책 승인

  Phase 1                 최소 동작 제품(MVP)     단일 사이트, FIFO, 동시
                                                  입장 제한, Waiting Room

  Phase 2                 Gateway 및 동적 라우팅  재기동 없는 라우트
                                                  변경, allowlist, 롤백

  Phase 3                 Control Plane           Admin UI, Control API,
                                                  MariaDB, 정책 버전

  Phase 4                 보안 통합               서명 정책 배포, mTLS,
                                                  입장 토큰, 내부 Ingress
                                                  검증

  Phase 5                 HA 및 운영 기능         Redis HA, Gateway/Queue
                                                  Engine 수평 확장,
                                                  모니터링

  Phase 6                 운영 품질 검증          침투 테스트, 부하·장애
                                                  테스트, 설치·복구
                                                  자동화
  -----------------------------------------------------------------------

보안 Zone 간 연결과 토큰 서명 신뢰 모델은 MVP 이후로 미루지 말고 Phase
0에서 먼저 확정한다.

## 16. 구현 전 확정해야 할 의사결정

  -------------------------------------------------------------------------
  ID                결정 항목         권장 기본안         확정 시 고려사항
  ----------------- ----------------- ------------------- -----------------
  DEC-01            Gateway와 Queue   별도                침해 확산, 확장,
                    Engine 배포       프로세스/컨테이너   운영 복잡성

  DEC-02            DMZ 정책 배포     내부에서 시작하는   실제
                    방식              승인된 서명 배포    방화벽/단방향
                                      채널 또는 단방향    장비 정책
                                      중계                

  DEC-03            입장 토큰 서명    내부 KMS/HSM 기반   발급 지연,
                                      비대칭 서명         가용성, 키 보호

  DEC-04            Redis HA          Sentinel 또는       처리량, RPO/RTO,
                                      Cluster             운영 역량

  DEC-05            실시간 대기 갱신  SSE                 동시 연결 수와
                                                          프록시 timeout

  DEC-06            Gateway           Spring Cloud        동적 라우팅 구현
                                      Gateway             및 운영 경험

  DEC-07            Admin 인증        Keycloak/OIDC + MFA 기존 IAM 연계

  DEC-08            내부 Ingress      기존 API Gateway/LB 별도 구축 필요
                                      우선 활용           여부

  DEC-09            장애 정책         사이트별            서비스 연속성
                                      Fail-closed 기본    요구
                                      검토                

  DEC-10            배포 플랫폼       Docker/Podman 또는  운영 표준 및
                                      Kubernetes          인프라 역량

  DEC-11            로그 전달         내부 Pull 또는      DMZ outbound 허용
                                      승인된 전용 수집    여부
                                      경로                

  DEC-12            서비스 식별       site_id +           멀티테넌시 및
                                      service_id +        정책 경계
                                      queue_id            
  -------------------------------------------------------------------------

## 17. 결론 및 권장 기준 아키텍처

OpenQueue는 다음 구조를 기본 설계 방향으로 한다.

1.  **DMZ:** 외부 Load Balancer/WAF, Edge Gateway, Queue Engine, Waiting
    Room, 대기열 전용 Redis
2.  **내부 관리망:** Admin Dashboard, Control API, 정책 검증·서명·배포,
    IAM, 모니터링 관리
3.  **DB Zone:** MariaDB, 감사 데이터 및 백업 저장소
4.  **내부 서비스망:** Internal API Ingress 및 기존 Business API/Origin
5.  **Zone 간 통신:** 기본 차단, 승인된 목적지·포트·프로토콜만 허용,
    TLS/mTLS 및 최소 권한
6.  **정책 관리:** 내부 Control Plane에서 버전·서명된 정책을 배포하고
    DMZ는 검증 후 메모리에 적용
7.  **대기열 상태:** DMZ Redis에 최소 정보만 저장하고 내부 MariaDB와
    직접 연계하지 않음
8.  **입장 권한:** 비대칭 서명 기반 토큰을 우선 검토하고 내부
    Ingress에서 최종 검증
9.  **라우팅:** 등록된 백엔드 allowlist만 사용하고 임의 URL 프록시를
    금지
10. **운영:** 노드별 정책 버전, 장애, 감사, 부하 및 복구 상태를 관리

이 구조의 가장 중요한 목표는 DMZ의 장애나 침해가 내부 관리망·DB
Zone·업무 서비스 전체의 침해로 이어지지 않게 하는 것이다. 기능 구현보다
먼저 네트워크 경계, 통신 허용 목록, 토큰 서명키의 보관 위치, 정책 배포
경로를 확정해야 한다.

------------------------------------------------------------------------

## 부록 A. 용어

  -----------------------------------------------------------------------
  용어                                설명
  ----------------------------------- -----------------------------------
  Virtual Waiting Room                접속 폭주 시 사용자를 대기시킨 후
                                      제한적으로 입장시키는 시스템

  Control Plane                       운영 정책과 설정을 생성·관리하는
                                      제어 영역

  Data Plane                          실제 사용자 요청과 대기열을
                                      처리하는 실행 영역

  FIFO                                먼저 등록된 요청을 먼저 처리하는
                                      방식

  Lease                               일정 시간 동안 유효한 임시 점유
                                      권한

  mTLS                                클라이언트와 서버가 상호 인증서를
                                      검증하는 TLS

  SSRF                                서버가 공격자 의도에 따라 내부 또는
                                      제한된 목적지로 요청을 보내게 하는
                                      취약점

  RPO                                 장애 시 허용 가능한 데이터 손실
                                      시점

  RTO                                 장애 발생 후 목표 복구 시간

  SBOM                                소프트웨어 구성요소 목록
  -----------------------------------------------------------------------

## 부록 B. 참고 문서

실제 도입 시 각 제품의 공식 문서, 지원 버전, 보안 권고 및 라이선스를
확인한다.

-   Spring Boot: https://spring.io/projects/spring-boot
-   Spring Cloud Gateway:
    https://spring.io/projects/spring-cloud-gateway
-   Spring Security: https://spring.io/projects/spring-security
-   Redis: https://redis.io/docs/
-   MariaDB: https://mariadb.org/documentation/
-   React: https://react.dev/
-   Keycloak: https://www.keycloak.org/documentation
-   Prometheus: https://prometheus.io/docs/
-   Grafana: https://grafana.com/docs/
-   OpenTelemetry: https://opentelemetry.io/docs/
-   OWASP Top 10: https://owasp.org/www-project-top-ten/
-   OWASP SSRF Prevention Cheat Sheet:
    https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html
