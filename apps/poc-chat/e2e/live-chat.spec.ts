import { expect, test, type Page } from '@playwright/test';

const runLiveSmoke = process.env.PLAYWRIGHT_LIVE === '1';

test.describe('smoke live do stack Hermes-first', () => {
  test.skip(!runLiveSmoke, 'execute com PLAYWRIGHT_LIVE=1 e PLAYWRIGHT_BASE_URL');

  test('conversa com três contatos reais, aguarda, alterna e recupera após reload', async ({ page }) => {
    test.setTimeout(240_000);
    const suffix = Date.now().toString(36);
    const names = [`Live A ${suffix}`, `Live B ${suffix}`, `Live C ${suffix}`];

    await page.goto('/');
    await createContact(page, names[0]);
    await sendMessage(page, 'Oi, quero testar uma conversa local.');
    await sendMessage(page, 'Estou enviando este segundo fragmento sem forçar o processamento.');
    await createContact(page, names[1]);
    await sendMessage(page, 'Oi, esta é uma conversa independente para validar isolamento.');
    await createContact(page, names[2]);
    await sendMessage(page, 'Oi, preciso de ajuda para entender os serviços da Urba.');

    for (const name of [...names].reverse()) {
      await selectContact(page, name);
      await waitForReply(page);
    }

    await page.reload();
    for (const name of names) {
      await selectContact(page, name);
      await waitForReply(page);
    }
    await expect(page.locator('.contact-button')).toHaveCount(3);
  });
});

async function createContact(page: Page, name: string): Promise<void> {
  await page.getByRole('textbox', { name: /nome do contato/i }).fill(name);
  await page.getByRole('button', { name: /criar contato/i }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

async function sendMessage(page: Page, text: string): Promise<void> {
  const composer = page.getByRole('textbox', { name: /mensagem/i });
  await composer.fill(text);
  await composer.press('Enter');
  await expect(page.getByText(text, { exact: true })).toBeVisible();
}

async function selectContact(page: Page, name: string): Promise<void> {
  await page.locator('.contact-button').filter({ hasText: name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

async function waitForReply(page: Page): Promise<void> {
  await expect.poll(
    () => page.locator('.message-bubble--received').count(),
    { timeout: 120_000, intervals: [1_000, 2_000, 5_000] },
  ).toBeGreaterThan(0);
}
