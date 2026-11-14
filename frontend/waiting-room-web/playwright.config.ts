import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E (Phase 3 게이트, CI 전용). Queue API 는 e2e 프로파일(메모리 저장소, 동시 입장 1명, lease 3s)로 띄운다.
 * 로컬(Windows IDE)에서는 실행하지 않는다 — CI `frontend-e2e` 잡이 실행한다.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: [
    {
      // 백엔드: 사전 빌드(mvn -q -DskipTests install)된 모듈을 e2e 프로파일로 실행한다.
      command: 'mvn -q -pl openqueue-queue-api spring-boot:run -Dspring-boot.run.profiles=e2e',
      cwd: '../../backend',
      url: 'http://localhost:8080/actuator/health',
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
