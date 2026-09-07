import { expect, test } from '@playwright/test';

const token = 'ui-test-token-that-never-enters-the-url';
const longTerms = '<script>not markup</script>\n' + 'Termos aprovados. '.repeat(500);

function mockApi(page: Parameters<typeof test>[0] extends never ? never : any,
  content = longTerms) {
  return Promise.all([
    page.route('**/api/terms/presentation', async (route: any) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        presentationId: 'presentation-ui', serviceType: 'DECOR_INTERIORES', termsVersion: 'v1',
        termsHash: 'hash-ui', termsResource: 'https://legal.example/terms', status: 'PAGE_PRESENTED',
        content,
      }) });
    }),
    page.route('**/api/terms/end-reached', async (route: any) => {
      await route.fulfill({ status: 204 });
    }),
    page.route('**/api/terms/decision', async (route: any) => {
      const body = route.request().postDataJSON();
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        presentationId: 'presentation-ui', status: body.decision === 'ACCEPT' ? 'ACCEPTED' : 'DECLINED',
        decision: body.decision, idempotent: false, continuationMessage: 'continuação enfileirada',
      }) });
    }),
  ]);
}

test('enables the accept button only after the server acknowledges the end', async ({ page }) => {
  await mockApi(page);
  await page.goto(`/termos/index.html#t=${token}`);

  await expect(page).not.toHaveURL(/#t=/);
  const content = page.locator('#terms-content');
  const accept = page.getByRole('button', { name: 'Li e aceito os termos' });
  await expect(content).toContainText('Termos aprovados.');
  await expect(content.locator('script')).toHaveCount(0);
  await expect(accept).toBeDisabled();

  await content.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
    element.dispatchEvent(new Event('scroll'));
  });
  await expect(accept).toBeEnabled();
  await accept.click();
  await expect(page.getByRole('status')).toContainText('Termos aceitos');
});

test('automatically records end for content that fits and keeps decline available', async ({ page }) => {
  await mockApi(page, 'Termos curtos aprovados.');
  await page.goto(`/termos/index.html#t=${token}`);

  await expect(page.locator('#terms-content')).toContainText('Termos curtos aprovados.');
  await expect(page.getByRole('button', { name: 'Li e aceito os termos' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Não aceito' })).toBeEnabled();
  await page.getByRole('button', { name: 'Não aceito' }).click();
  await expect(page.getByRole('status')).toContainText('Tudo bem');
});

test('does not unlock acceptance when end confirmation fails', async ({ page }) => {
  await page.route('**/api/terms/presentation', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      presentationId: 'presentation-ui', serviceType: 'DECOR_INTERIORES', termsVersion: 'v1',
      termsHash: 'hash-ui', termsResource: 'https://legal.example/terms', status: 'PAGE_PRESENTED', content: longTerms,
    }) });
  });
  await page.route('**/api/terms/end-reached', async (route) => {
    await route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"unavailable"}' });
  });
  await page.goto(`/termos/index.html#t=${token}`);
  const content = page.locator('#terms-content');
  const accept = page.getByRole('button', { name: 'Li e aceito os termos' });
  await expect(content).toContainText('Termos aprovados.');
  await content.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
    element.dispatchEvent(new Event('scroll'));
  });
  await expect(accept).toBeDisabled();
  await expect(page.getByRole('status')).toContainText('Não foi possível confirmar');
});
