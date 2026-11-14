# waiting-room-web

DMZ 사용자 대기 화면 (WUI-001/002). UI 라이브러리 없이 React + Vite + TypeScript.

```bash
npm ci
npm run dev        # http://localhost:5173/demo/main  (/queue → localhost:8080 프록시)
npm test           # Vitest + React Testing Library
npm run e2e        # Playwright (Queue API 를 e2e 프로파일로 띄운 뒤, CI 에서 실행)
```

- 경로 `/{siteId}/{queueId}` 로 대기열을 식별한다(Gateway 가 이 경로로 보냄). 세션은 HttpOnly 쿠키만 사용하므로 JS 는 세션 ID 를 모른다.
- SSE(`…/session/events`)로 순번을 받고, 연결이 영구 실패하면 5초 폴링(heartbeat)으로 내려간다.
- API 계약: `docs/api/queue-api.md`
