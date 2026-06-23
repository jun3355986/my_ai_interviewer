import { expect, test } from '@playwright/test';

import { AppShell } from '../pages/AppShell';

test('admin web app renders entry surface', async ({ page }) => {
  const app = new AppShell(page);

  await app.gotoHome();
  await app.expectPageRendered();

  await expect(page.getByText(/登录|管理|Admin|AI/i).first()).toBeVisible();
  await app.expectNoConsoleErrors();
});

test('admin web app shows AI observability traces, detail, stats, and reveals raw payloads on demand', async ({
  page,
}) => {
  const app = new AppShell(page);
  const traceId = '11111111-1111-1111-1111-111111111111';
  const callId = '22222222-2222-2222-2222-222222222222';
  const rawRequests: string[] = [];

  await page.addInitScript(() => {
    localStorage.setItem('ai_interviewer_admin_token', 'smoke-token');
    localStorage.setItem(
      'ai_interviewer_admin_profile',
      JSON.stringify({ id: 1, username: 'admin', nickname: 'Smoke Admin' }),
    );
  });
  await page.route('**/admin/dashboard/overview', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'OK',
        data: {
          userCount: 1,
          jobCount: 1,
          resumeCount: 1,
          interviewCount: 1,
          evaluationCount: 1,
          scoreDistribution: [],
          interviewTrend: [],
        },
      }),
    });
  });
  await page.route('**/admin/ai-observability/stats**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'OK',
        data: {
          traceCount: 7,
          totalLlmCalls: 11,
          totalTokens: 12345,
          failedLlmCalls: 2,
          llmFailureRate: 0.1818,
          averageLatencyMs: 345.6,
          providerPromptCacheTokenHitRate: 0.4567,
          providerPromptCacheCallHitRate: 0.3333,
          providerCacheUnreportedCalls: 3,
        },
      }),
    });
  });
  await page.route('**/admin/ai-observability/traces**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'OK',
        data: {
          current: 1,
          size: 10,
          total: 1,
          pages: 1,
          records: [
            {
              id: traceId,
              requestId: 'req-smoke',
              userId: 9,
              username: 'candidate',
              sessionId: 'session-smoke',
              businessType: 'INTERVIEW',
              entrypoint: 'generate-question',
              status: 'SUCCESS',
              totalTokens: 2048,
              llmCallCount: 1,
              provider: 'list-provider',
              model: 'list-model',
              providerPromptCacheTokenHitRate: 0.4567,
              providerPromptCacheCallHitRate: 0.3333,
              durationMs: 678,
              startedAt: '2026-06-23T10:00:00+08:00',
              fallbackUsed: true,
            },
          ],
        },
      }),
    });
  });
  await page.route(`**/admin/ai-observability/traces/${traceId}`, async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'OK',
        data: {
          id: traceId,
          requestId: 'req-smoke',
          username: 'candidate',
          sessionId: 'session-smoke',
          businessType: 'INTERVIEW',
          entrypoint: 'generate-question',
          status: 'SUCCESS',
          totalTokens: 2048,
          durationMs: 678,
          startedAt: '2026-06-23T10:00:00+08:00',
          steps: [
            {
              id: '33333333-3333-3333-3333-333333333333',
              stepOrder: 1,
              stepType: 'PROMPT_BUILD',
              stepName: '构造提示词',
              status: 'SUCCESS',
              durationMs: 123,
              startedAt: '2026-06-23T10:00:00+08:00',
            },
          ],
          llmCalls: [
            {
              id: callId,
              traceId,
              callType: 'CHAT',
              provider: 'deepseek',
              model: 'deepseek-chat',
              status: 'SUCCESS',
              totalTokens: 2048,
              promptTokens: 1024,
              completionTokens: 1024,
              latencyMs: 555,
              promptCacheHitRate: 0.5,
              cacheReportedByProvider: true,
              fallbackUsed: true,
              fallbackFromModel: 'deepseek-reasoner',
            },
          ],
        },
      }),
    });
  });
  await page.route(`**/admin/ai-observability/llm-calls/${callId}/raw**`, async (route) => {
    rawRequests.push(route.request().url());
    const url = new URL(route.request().url());
    const type = url.searchParams.get('type') || 'PROMPT';
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'OK',
        data: {
          callId,
          traceId,
          accessType: type,
          rawText: type === 'PROMPT' ? 'raw prompt smoke payload' : 'raw response smoke payload',
        },
      }),
    });
  });

  await app.gotoHome();
  await page.getByRole('button', { name: /AI 观测/ }).click();

  await expect(page.getByRole('heading', { name: 'AI 观测' })).toBeVisible();
  await expect(page.getByText('Provider Cache Token Hit Rate')).toBeVisible();
  await expect(page.getByText('Provider Cache Call Hit Ratio')).toBeVisible();
  await expect(page.getByText('Provider Cache Unreported Calls')).toBeVisible();
  const traceRow = page.getByRole('row').filter({ hasText: 'session-smoke' });
  await expect(traceRow).toBeVisible();
  await expect(traceRow.getByText('list-provider / list-model')).toBeVisible();
  await expect(traceRow.getByText('45.67%')).toBeVisible();
  await expect(traceRow.getByText('33.33% calls')).toBeVisible();
  await expect(traceRow.getByText('2,048')).toBeVisible();
  await expect(traceRow.getByText('678ms')).toBeVisible();

  await traceRow.getByRole('button', { name: '查看' }).click();

  await expect(page.getByText('构造提示词')).toBeVisible();
  await expect(page.getByText('deepseek-reasoner')).toBeVisible();
  expect(rawRequests).toEqual([]);

  await page.getByRole('button', { name: 'Reveal Prompt' }).click();
  await expect(page.getByText('raw prompt smoke payload')).toBeVisible();
  expect(rawRequests.some((url) => url.includes('type=PROMPT'))).toBeTruthy();

  await page.getByRole('button', { name: 'Reveal Response' }).click();
  await expect(page.getByText('raw response smoke payload')).toBeVisible();
  expect(rawRequests.some((url) => url.includes('type=RESPONSE'))).toBeTruthy();
  await app.expectNoConsoleErrors();
});
