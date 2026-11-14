# deployment

Zone별 배포 정의. 서로 다른 Zone의 구성요소를 같은 배포 단위(컨테이너·Pod·서비스 계정)로 묶지 않는다.

| 디렉터리 | Zone | 구성요소 |
|---|---|---|
| `dmz/` | DMZ | Edge Gateway, Queue Engine, Waiting Room, Queue Redis |
| `management/` | 내부 관리망 | Control API, Admin Web, 정책 서명·배포 |
| `db-zone/` | DB Zone | MariaDB, 감사 저장소 |
| `internal-ingress/` | 내부 서비스망 | Internal API Ingress 연계 설정 |

`dmz/` 아래에는 DB 접속 정보·관리자 자격증명·서명 개인키를 두지 않는다. 비밀정보는 외부 주입한다. 배포 플랫폼: DEC-10 (미결정).
