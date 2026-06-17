import { expect, type Page } from '@playwright/test';

export class AppShell {
  constructor(private readonly page: Page) {}

  async gotoHome() {
    const response = await this.page.goto('/', { waitUntil: 'networkidle' });
    expect(response?.ok(), `home page should load: ${this.page.url()}`).toBeTruthy();
  }

  async expectPageRendered() {
    await expect(this.page.locator('body')).toBeVisible();
    const bodyText = (await this.page.locator('body').innerText()).trim();
    expect(bodyText.length, 'page body should contain visible text').toBeGreaterThan(0);
  }

  async expectNoConsoleErrors() {
    const errors: string[] = [];
    this.page.on('console', (message) => {
      if (message.type() === 'error') {
        errors.push(message.text());
      }
    });
    await this.page.waitForTimeout(500);
    expect(errors, 'browser console errors').toEqual([]);
  }
}
