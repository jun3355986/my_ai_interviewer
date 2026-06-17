import { expect, test } from '@playwright/test';

import { AppShell } from '../pages/AppShell';

test('admin web app renders entry surface', async ({ page }) => {
  const app = new AppShell(page);

  await app.gotoHome();
  await app.expectPageRendered();

  await expect(page.getByText(/登录|管理|Admin|AI/i).first()).toBeVisible();
  await app.expectNoConsoleErrors();
});
