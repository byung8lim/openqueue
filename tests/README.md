# tests (모듈 횡단 테스트)

| 디렉터리 | 내용 | 도입 |
|---|---|---|
| `integration/` | 여러 모듈·컨테이너 결합 테스트 | Phase 2 |
| `contract/` | API·정책 schema·토큰 claim 계약 | Phase 3~6 |
| `security/` | Zone 차단, SSRF, 토큰 위조·재사용 등 | Phase 4~8 |
| `performance/` | 부하·failover | Phase 7 |
| `e2e/` | Playwright 사용자 시나리오 | Phase 3 |

모듈 내부 단위·통합 테스트는 각 모듈의 `src/test`에 둔다. 전략: [test-strategy.md](../docs/test/test-strategy.md)
