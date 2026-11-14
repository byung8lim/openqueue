# DB Migration 정책

- 문서 버전: 0.1 (TASK-000, Phase 5에서 상세화)
- 대상: DB Zone의 MariaDB (Control Plane 전용). **DMZ 구성요소는 이 DB에 접근하지 않는다.**

## 1. 규칙

1. 모든 DDL/DML은 `database/migration/` 아래 Flyway 파일로 관리한다.
   - 파일명: `V<YYYYMMDDHHmm>__<snake_case_설명>.sql` (예: `V202611140900__create_site.sql`)
   - 반복 실행 스크립트(뷰 등): `R__<설명>.sql`
2. Java 코드에 SQL 문자열을 하드코딩하지 않는다. Repository가 사용하는 쿼리도 별도 `.sql` 리소스 또는
   선택된 DB 접근 기술(JPA/jOOQ, Phase 5 ADR)의 타입 안전 API를 사용한다.
3. 동적 조건은 파라미터 바인딩만 사용한다. 식별자(테이블·컬럼명)를 외부 입력으로 조합하지 않는다.
4. 이미 적용된 migration 파일은 수정하지 않는다. 변경은 새 버전으로 추가한다.
5. **추가 방식 우선**: 컬럼 삭제·이름 변경·타입 축소처럼 데이터 손실 가능 변경은 별도 승인과 복구 계획이 필요하다(I3).
6. 테이블·컬럼·인덱스·제약조건 변경 시 [ERD.md](ERD.md)와 데이터 사전을 같은 PR에서 수정한다.
7. 시드 데이터(`database/seed/`)에는 비밀정보·실제 개인정보를 넣지 않는다.

## 2. 검증

- CI: Testcontainers MariaDB에 전체 migration을 빈 DB부터 적용하는 테스트 (Phase 5)
- Repository 통합 테스트는 migration이 적용된 스키마에서 실행한다.

## 3. 실패 복구

- Flyway 실패 시 `flyway_schema_history`의 실패 레코드를 확인하고, 원인 수정 후 `repair`는 승인된 운영자만 수행한다.
- 운영 적용 전 백업을 필수로 하고, 절차는 `docs/operations/backup-restore.md`(Phase 7)에 기록한다.
