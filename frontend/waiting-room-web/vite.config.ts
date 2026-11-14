/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 개발 서버는 /queue 경로만 로컬 Queue API(8080)로 프록시한다. 운영에서는 Gateway(Phase 4)가 같은 origin 으로 묶는다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/queue': { target: 'http://localhost:8080', changeOrigin: false } },
  },
  build: { sourcemap: false, target: 'es2022' },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: { provider: 'v8', reporter: ['text', 'lcov'] },
  },
});
