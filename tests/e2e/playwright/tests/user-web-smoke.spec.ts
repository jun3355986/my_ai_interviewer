import { expect, test } from '@playwright/test';

import { AppShell } from '../pages/AppShell';

test('user web app renders login surface', async ({ page }) => {
  const app = new AppShell(page);

  await app.gotoHome();
  await app.expectPageRendered();

  await expect(page.getByText(/AI 面试官助手|登录|面试/i).first()).toBeVisible();
  await app.expectNoConsoleErrors();
});
