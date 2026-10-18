import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', workers: 1, timeout: 120000,
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18080/api/v1/', actionTimeout: 15000, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  reporter: [['list'], ['html', { open: 'never' }]],
});
