# API 오류 및 로그 규약

- 문서 버전: 0.1 (TASK-000 초안, Phase 3 API 착수 전 승인 필요)
- 관련: SEC-001, SEC-002, OBS-001, 개발요청서 §11

## 1. API 버전·경로

| 구분 | 경로 접두어 | Zone | 인증 |
|---|---|---|---|
| 공개 Queue API | `/queue/v1/...` | DMZ | 대기 세션 쿠키(HttpOnly, Secure, SameSite) |
| 내부 관리 API | `/control/v1/...` | 내부 관리망 | OIDC Bearer + RBAC |

- 하위 호환이 깨지는 변경은 메이저 버전을 올린다 (`v1` → `v2`).
- 공개 API와 관리 API는 **같은 프로세스에 함께 두지 않는다**.

## 2. 오류 응답 형식

RFC 9457 (Problem Details) 기반:

```json
{
  "type": "urn:openqueue:problem:invalid-input",
  "title": "입력값이 올바르지 않습니다.",
  "status": 400,
  "code": "QUE-INPUT-001",
  "requestId": "01JC9X...",
  "detail": "site_id 형식이 올바르지 않습니다."
}
```

규칙:

1. `code`는 `<영역>-<분류>-<번호>` 형식으로 오류 코드 카탈로그(Phase 1에서 `openqueue-common`에 정의)에 등록한다.
2. `detail`에는 **필드명만** 쓰고 입력 원문을 반영하지 않는다 (로그·응답 인젝션 방지).
3. 스택 트레이스, 내부 호스트명, SQL, Redis key, 버전 정보를 응답에 넣지 않는다.
4. 인증·인가 실패는 원인을 구분하지 않는다 (401/403만, 계정 존재 여부 노출 금지).
5. 5xx 응답은 일반 메시지 + `requestId`만 반환하고 상세는 내부 로그로만 남긴다.

| HTTP | 사용 조건 |
|---|---|
| 400 | 입력 형식 오류 |
| 401 / 403 | 인증 실패 / 권한 없음 |
| 404 | 대상 없음 (권한 없는 리소스도 IDOR 방지를 위해 404 고려) |
| 409 | 상태 충돌 (예: 이미 입장한 세션의 취소) |
| 429 | rate limit 초과 (`Retry-After` 포함) |
| 503 | 대기열 장애 + Fail-closed 정책 |

## 3. 로그 규약

- 형식: JSON 구조화 로그, UTF-8, 시간은 UTC ISO-8601.
- 필수 필드: `timestamp`, `level`, `service`, `zone`, `requestId`, `traceId`, `event`, `siteId`, `queueId`
- `requestId`: Gateway가 생성하며, 외부에서 들어온 값은 형식 검증 후에만 신뢰한다 (OBS-001).

### 기록 금지·마스킹 대상

| 항목 | 처리 |
|---|---|
| 입장 토큰, JWT, Authorization 헤더, 쿠키 | **기록 금지** |
| 비밀번호, 키, Redis/DB 접속 정보 | **기록 금지** |
| 대기 세션 ID | 앞 4자 + `****` (도메인 `WaitingSessionId.toString()`이 이미 마스킹) |
| 클라이언트 IP | 신뢰 프록시 체인 검증 후 기록, 보존 기간 정책 적용 (OD-08) |
| 외부 입력 문자열 | 개행·제어문자 이스케이프 후 길이 제한 |

### 감사 로그(관리자 작업)

관리자 로그인, 정책 변경, 권한 변경, 배포, 롤백은 별도 감사 이벤트로 DB Zone 감사 저장소에 기록한다.
DMZ는 감사 기록을 삭제·수정할 수 없어야 한다.
