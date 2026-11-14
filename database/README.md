# database (DB Zone - MariaDB)

- `migration/`: Flyway SQL (`V<YYYYMMDDHHmm>__<설명>.sql`)
- `seed/`: 개발용 시드 데이터 (비밀정보·실제 개인정보 금지)
- `test/`: migration 검증용 SQL

규칙: [migration-policy.md](../docs/database/migration-policy.md). Phase 5에서 시작한다. **DMZ 구성요소는 이 DB에 접근하지 않는다.**
