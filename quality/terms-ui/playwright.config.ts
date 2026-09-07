import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 15_000,
  fullyParallel: true,
  reporter: process.env.CI ? 'line' : 'list',
  use: {
    baseURL: process.env.TERMS_UI_BASE_URL ?? 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    ...(process.env.TERMS_UI_BROWSER_PATH
      ? { launchOptions: { executablePath: process.env.TERMS_UI_BROWSER_PATH } }
      : {}),
    ...devices['Desktop Chrome'],
  },
  webServer: process.env.TERMS_UI_BASE_URL
    ? undefined
    : {
        command: 'python3 -m http.server 4173 --directory ../../apps/urbana-connect-api/src/main/resources/static',
        url: 'http://127.0.0.1:4173/termos/index.html',
        reuseExistingServer: true,
      },
});
