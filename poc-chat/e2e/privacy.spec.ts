import { expect, test } from '@playwright/test';

test('não expõe nome amigável, token ou transcript persistente ao browser', async ({ page }) => {
  const displayName = 'Nome local confidencial';
  const requests: Array<{ url: string; headers: Record<string, string>; body: string }> = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/poc/conversations/')) {
      requests.push({
        url: request.url(),
        headers: request.headers(),
        body: request.postData() ?? '',
      });
    }
  });
  await page.route('**/api/poc/conversations/**', async (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { eventId: string };
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ eventId: body.eventId, correlationId: 'privacy-correlation', status: 'QUEUED' }),
      });
      return;
    }
    const alias = new URL(route.request().url()).pathname.split('/')[4] ?? '';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ contactId: `poc:${alias}`, conversation: {}, messages: [] }),
    });
  });

  await page.goto('/');
  await page.getByRole('textbox', { name: /nome do contato/i }).fill(displayName);
  await page.getByRole('button', { name: /criar contato/i }).click();
  await page.getByRole('textbox', { name: /mensagem/i }).fill('Mensagem permitida');
  await page.getByRole('textbox', { name: /mensagem/i }).press('Enter');
  await expect(page.getByText('Mensagem permitida', { exact: true })).toBeVisible();

  const stored = await page.evaluate(() => localStorage.getItem('urbana.poc-chat.v1'));
  expect(stored).toContain(displayName);
  expect(stored).not.toMatch(/token|transcript|eventId|correlationId|authorization/i);
  expect(requests.length).toBeGreaterThan(0);
  const requestText = JSON.stringify(requests);
  expect(requestText).not.toContain(displayName);
  expect(requestText).not.toMatch(/authorization|flush|metrics|payment-proof/i);
});
