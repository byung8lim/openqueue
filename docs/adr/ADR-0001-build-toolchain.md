# ADR-0001: 빌드 도구 및 런타임 버전

- 상태: 제안 (TASK-000에서 작성, 승인 필요)
- 작성일: 2026-11-14
- 관련: 개발요청서 §5, §17 작업범위 3
- 영향도 등급: I2

## 배경

개발요청서는 "Maven 또는 Gradle 중 하나를 초기 확정"하고 Java 21 LTS, React+TypeScript+Vite를 권장한다.

## 검토한 선택지

| 선택지 | 장점 | 단점 |
|---|---|---|
| Maven | 요청서 §6 구조가 `backend/pom.xml` 기준, 선언적이라 검토가 쉬움, enforcer로 모듈 경계 강제 용이, 개발 PC에 설치됨 | 빌드 커스터마이즈가 장황 |
| Gradle | 빌드 속도, 유연성 | 스크립트형 빌드는 리뷰 난도↑, 개발 PC 미설치 |

## 결정

| 영역 | 결정 |
|---|---|
| Java 빌드 | **Maven 3.9+**, 멀티모듈, parent `backend/pom.xml`에서 모든 버전 고정 |
| Java 대상 | **Java 21 LTS** (`maven.compiler.release=21`). 상위 JDK로 빌드해도 21 API만 사용 가능 |
| 컴파일 | `-Xlint:all -Werror` (경고를 오류로 처리) |
| Frontend 빌드 | **npm + Vite + TypeScript** (Phase 3에서 스캐폴딩). 패키지 매니저는 npm으로 고정해 lockfile 일원화 |
| groupId / 패키지 | `com.wbl.openqueue` (가칭. OD-02에서 확정) |

## 결과와 영향

- 개발 PC에서 JDK 25로 빌드해도 결과물은 Java 21 바이트코드이며, CI는 JDK 21(Temurin)로 검증한다.
- Spring Boot 3.x / Spring Cloud Gateway 버전은 해당 모듈이 처음 생기는 Phase에서 공식 호환성 매트릭스를 확인 후 고정한다(ADR 추가).
- 되돌리기: Gradle 전환 시 모든 모듈 빌드 파일 재작성 필요 → 조기 확정이 중요.
