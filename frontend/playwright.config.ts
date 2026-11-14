import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests', workers: 1, fullyParallel: false, timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['json', { outputFile: process.env.R07_RESULT || 'test-results/results.json' }]],
  use: { baseURL: 'http://localhost:3000', trace: 'off', screenshot: 'off' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: { command: 'npm run dev -- --host 127.0.0.1 --strictPort', url: 'http://localhost:3000', reuseExistingServer: false },
});
