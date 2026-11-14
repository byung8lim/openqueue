# ERD 및 데이터 사전

- 상태: **미작성** — Phase 5 (DB-001) 착수 시 작성한다.
- 예정 테이블(설계서 §11): `site`, `backend_service`, `queue_policy`, `route_policy`, `policy_version`, `deployment`, `node`, `admin_role`, `audit_event`
- 규칙: [migration-policy.md](migration-policy.md)

## Redis 데이터 (참고, DB가 아닌 DMZ 저장소)

Redis key namespace·자료구조·TTL은 Phase 2 (QUE-008)에서 `docs/database/redis-keys.md`로 별도 문서화한다.
key에는 반드시 `site_id`, `queue_id` 범위를 포함한다. Redis를 영속 정책 DB로 사용하지 않는다.
