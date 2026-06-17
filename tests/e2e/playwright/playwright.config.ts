import { defineConfig, devices } from '@playwright/test';

const userBaseURL = process.env.USER_WEB_BASE_URL || 'http://localhost:8088';
const adminBaseURL = process.env.ADMIN_WEB_BASE_URL || 'http://localhost:8090';

export default defineConfig({
  testDir: './tests',
  timeout: 45_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: '../../reports/playwright/html', open: 'never' }],
    ['junit', { outputFile: '../../reports/playwright/junit.xml' }],
  ],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
  },
  projects: [
    {
      name: 'user-web-chromium',
      testMatch: /user-.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: userBaseURL,
      },
    },
    {
      name: 'admin-web-chromium',
      testMatch: /admin-.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: adminBaseURL,
      },
    },
  ],
  outputDir: '../../reports/playwright/artifacts',
});
