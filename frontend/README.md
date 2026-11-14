# frontend

- `admin-web/`: 내부 관리망 관리자 UI (Phase 5, ADM-*)
- `waiting-room-web/`: DMZ 사용자 대기 화면 (Phase 3, WUI-*)

빌드 도구: npm + Vite + React + TypeScript ([ADR-0001](../docs/adr/ADR-0001-build-toolchain.md)). 테스트: Vitest, React Testing Library, Playwright.
두 앱은 서로 다른 Zone에 배포되므로 코드·빌드 산출물·설정을 공유하지 않는다. 스캐폴딩은 해당 Phase에서 수행한다.
