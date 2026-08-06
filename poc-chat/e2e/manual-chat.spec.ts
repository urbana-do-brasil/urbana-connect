import { expect, test, type Page } from '@playwright/test';

type StoredMessage = {
  id: string;
  eventId: string;
  correlationId: string;
  contactId: string;
  direction: 'INBOUND' | 'OUTBOUND';
  senderType: 'CONTACT' | 'URBA';
  type: 'TEXT';
  text: string;
  createdAt: string;
};

function aliasFromUrl(url: string): string {
  return new URL(url).pathname.split('/')[4] ?? '';
}

function projection(alias: string, messages: StoredMessage[]) {
  return {
    contactId: `poc:${alias}`,
    conversation: {},
    messages,
  };
}

function inboundMessage(alias: string, eventId: string, text: string): StoredMessage {
  return {
    id: `${eventId}-canonical`,
    eventId,
    correlationId: `${alias}-${eventId}-corr`,
    contactId: `poc:${alias}`,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text,
    createdAt: new Date().toISOString(),
  };
}

function responseMessage(inbound: StoredMessage, text: string): StoredMessage {
  return {
    ...inbound,
    id: `${inbound.id}-response`,
    eventId: `${inbound.eventId}-response`,
    direction: 'OUTBOUND',
    senderType: 'URBA',
    text,
  };
}

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

test.describe('simulador local manual', () => {
  test('US1: cria contato, envia fragmentos e renderiza a resposta canônica', async ({ page }) => {
    const messages = new Map<string, StoredMessage[]>();
    await page.route('**/api/poc/conversations/**', async (route) => {
      const alias = aliasFromUrl(route.request().url());
      const current = messages.get(alias) ?? [];
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON() as { eventId: string; text: string };
        messages.set(alias, [...current, inboundMessage(alias, body.eventId, body.text)]);
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          body: JSON.stringify({ eventId: body.eventId, correlationId: `${alias}-corr`, status: 'QUEUED' }),
        });
        return;
      }
      const latest = messages.get(alias) ?? [];
      const firstInbound = latest.find((message) => message.direction === 'INBOUND');
      const withResponse = firstInbound && !latest.some((message) => message.direction === 'OUTBOUND')
        ? [...latest, responseMessage(firstInbound, 'Resposta canônica da Urba')]
        : latest;
      messages.set(alias, withResponse);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(projection(alias, withResponse)),
      });
    });

    await page.goto('/');
    await createContact(page, 'Teste');
    await sendMessage(page, 'fragmento 1');
    await sendMessage(page, 'fragmento 2');

    await expect(page.getByText('Resposta canônica da Urba', { exact: true })).toBeVisible({ timeout: 5_000 });
    expect(page.url()).not.toContain('flush');
  });

  test('US2: mantém três contatos isolados, inclusive nomes iguais, e marca não lidos', async ({ page }) => {
    const messages = new Map<string, StoredMessage[]>();
    let releaseResponses = false;
    await page.route('**/api/poc/conversations/**', async (route) => {
      const alias = aliasFromUrl(route.request().url());
      const current = messages.get(alias) ?? [];
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON() as { eventId: string; text: string };
        messages.set(alias, [...current, inboundMessage(alias, body.eventId, body.text)]);
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          body: JSON.stringify({ eventId: body.eventId, correlationId: `${alias}-corr`, status: 'QUEUED' }),
        });
        return;
      }
      let next = messages.get(alias) ?? [];
      if (releaseResponses) {
        const inbound = next.filter((message) => message.direction === 'INBOUND');
        const responses = inbound
          .filter((message) => !next.some((candidate) => candidate.id === `${message.id}-response`))
          .map((message) => responseMessage(message, `Resposta para ${message.text}`));
        next = [...next, ...responses];
        messages.set(alias, next);
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(projection(alias, next)),
      });
    });

    await page.goto('/');
    await createContact(page, 'Mesmo nome');
    await sendMessage(page, 'Primeira 1');
    await sendMessage(page, 'Primeira 2');
    await createContact(page, 'Mesmo nome');
    await sendMessage(page, 'Segunda 1');
    await sendMessage(page, 'Segunda 2');
    await createContact(page, 'Terceiro');
    await sendMessage(page, 'Terceira 1');

    releaseResponses = true;
    await page.waitForTimeout(2_500);
    await expect(page.getByLabel('Não lidas')).toHaveCount(2);

    const sameNameContacts = page.locator('.contact-button').filter({ hasText: 'Mesmo nome' });
    await sameNameContacts.nth(0).click();
    await expect(page.getByText('Resposta para Primeira 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Resposta para Segunda 1', { exact: true })).not.toBeVisible();
    await sameNameContacts.nth(1).click();
    await expect(page.getByText('Resposta para Segunda 2', { exact: true })).toBeVisible();
    await expect(page.getByText('Resposta para Primeira 2', { exact: true })).not.toBeVisible();
  });

  test('US3: recarrega contatos e recupera uma projeção canônica sem duplicação', async ({ page }) => {
    const messages = new Map<string, StoredMessage[]>();
    let deleteRequests = 0;
    page.on('request', (request) => {
      if (request.method() === 'DELETE') {
        deleteRequests += 1;
      }
    });
    await page.route('**/api/poc/conversations/**', async (route) => {
      const alias = aliasFromUrl(route.request().url());
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON() as { eventId: string; text: string };
        const inbound = inboundMessage(alias, body.eventId, body.text);
        messages.set(alias, [inbound, responseMessage(inbound, 'Resposta persistida')]);
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          body: JSON.stringify({ eventId: body.eventId, correlationId: inbound.correlationId, status: 'QUEUED' }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(projection(alias, messages.get(alias) ?? [])),
      });
    });

    await page.goto('/');
    await createContact(page, 'Histórico');
    await sendMessage(page, 'Mensagem persistida');
    await expect(page.getByText('Resposta persistida', { exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Histórico' })).toBeVisible();
    await expect(page.getByText('Resposta persistida', { exact: true })).toHaveCount(1);

    page.once('dialog', (dialog) => {
      void dialog.accept();
    });
    await page.getByRole('button', { name: /ocultar contato/i }).click();
    expect(deleteRequests).toBe(0);
  });

  test('US4: mantém processamento em segundo plano e entrega respostas fora de ordem', async ({ page }) => {
    const messages = new Map<string, StoredMessage[]>();
    const getCounts = new Map<string, number>();
    const responseDelays = new Map<string, number>();
    let postOrder = 0;
    await page.route('**/api/poc/conversations/**', async (route) => {
      const alias = aliasFromUrl(route.request().url());
      const current = messages.get(alias) ?? [];
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON() as { eventId: string; text: string };
        postOrder += 1;
        responseDelays.set(alias, postOrder === 1 ? 800 : postOrder === 2 ? 300 : 0);
        messages.set(alias, [...current, inboundMessage(alias, body.eventId, body.text)]);
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          body: JSON.stringify({ eventId: body.eventId, correlationId: `${alias}-corr`, status: 'QUEUED' }),
        });
        return;
      }
      const count = (getCounts.get(alias) ?? 0) + 1;
      getCounts.set(alias, count);
      let next = messages.get(alias) ?? [];
      if (count >= 3 && next.some((message) => message.direction === 'INBOUND')
        && !next.some((message) => message.direction === 'OUTBOUND')) {
        await new Promise((resolve) => setTimeout(resolve, responseDelays.get(alias) ?? 0));
        const inbound = next.find((message) => message.direction === 'INBOUND');
        if (inbound) {
          next = [...next, responseMessage(inbound, `Resposta ${inbound.text}`)];
          messages.set(alias, next);
        }
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(projection(alias, next)),
      });
    });

    await page.goto('/');
    await createContact(page, 'Primeiro');
    await sendMessage(page, 'Primeiro pendente');
    await createContact(page, 'Segundo');
    await sendMessage(page, 'Segundo pendente');
    await createContact(page, 'Terceiro');
    await sendMessage(page, 'Terceiro pendente');

    await expect(page.getByText('Resposta Terceiro pendente', { exact: true })).toBeVisible({ timeout: 5_000 });
    await expect(page.getByLabel('Não lidas')).toHaveCount(2);
    await page.locator('.contact-button').filter({ hasText: 'Primeiro' }).click();
    await expect(page.getByText('Resposta Primeiro pendente', { exact: true })).toBeVisible();
    await page.locator('.contact-button').filter({ hasText: 'Segundo' }).click();
    await expect(page.getByText('Resposta Segundo pendente', { exact: true })).toBeVisible();
  });

  test('US5: sinaliza falha técnica e recupera com o mesmo eventId sem duplicar', async ({ page }) => {
    let postAttempts = 0;
    let canonical: StoredMessage[] = [];
    await page.route('**/api/poc/conversations/**', async (route) => {
      const alias = aliasFromUrl(route.request().url());
      if (route.request().method() === 'POST') {
        postAttempts += 1;
        if (postAttempts <= 2) {
          await route.fulfill({ status: 504, body: '' });
          return;
        }
        const body = route.request().postDataJSON() as { eventId: string; text: string };
        const inbound = inboundMessage(alias, body.eventId, body.text);
        canonical = [inbound, responseMessage(inbound, 'Resposta recuperada')];
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          body: JSON.stringify({ eventId: body.eventId, correlationId: inbound.correlationId, status: 'QUEUED' }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(projection(alias, canonical)),
      });
    });

    await page.goto('/');
    await createContact(page, 'Falha');
    await sendMessage(page, 'Falha recuperável');
    await expect(page.getByRole('alert')).toContainText(/problema técnico/i);
    await page.getByRole('button', { name: /tentar novamente/i }).click();
    await expect(page.getByText('Resposta recuperada', { exact: true })).toBeVisible({ timeout: 5_000 });
    await expect(page.getByText('Falha recuperável', { exact: true })).toHaveCount(1);
    await expect(page.getByText('Resposta recuperada', { exact: true })).toHaveCount(1);
    expect(postAttempts).toBe(3);
  });
});
