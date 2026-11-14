#!/usr/bin/env bash
# OpenQueue 로컬 검증 게이트 (OD-03: GitHub 이관 전까지 CI 대신 서버에서 실행)
# CI(.github/workflows/ci.yml)의 backend-verify 잡과 동일한 명령을 실행한다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend"
echo "[verify] JDK: $(java -version 2>&1 | head -1)"
mvn -B -ntp clean verify
echo "[verify] 완료. 리포트: backend/*/target/{surefire-reports,site/jacoco,checkstyle-result.xml,spotbugsXml.xml}"
