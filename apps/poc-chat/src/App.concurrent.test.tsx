import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { createContact, saveUiState } from './state/contactStore';
import { jsonResponse } from './test/httpTestUtils';

const ALIAS_A = 'manual-11111111-1111-4111-8111-111111111111';
const ALIAS_B = 'manual-22222222-2222-4222-8222-222222222222';

function message(alias: string, overrides: Record<string, unknown> = {}) {
  return {
    id: `${alias}-inbound`,
    eventId: `${alias}-event`,
    correlationId: `${alias}-correlation`,
    contactId: `poc:${alias}`,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: `Mensagem ${alias}`,
    createdAt: '2026-08-06T12:00:00.000Z',
    ...overrides,
  };
}

describe('App concurrent conversations', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('keeps an inactive response isolated and marks only that contact unread', async () => {
    const first = createContact('Primeiro', () => '11111111-1111-4111-8111-111111111111');
    const second = createContact('Segundo', () => '22222222-2222-4222-8222-222222222222');
    saveUiState({
      schemaVersion: 1,
      contacts: [{ ...first, contactAlias: ALIAS_A }, { ...second, contactAlias: ALIAS_B }],
      activeContactAlias: ALIAS_A,
    });
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const alias = String(input).split('/').at(-1) ?? '';
      const inbound = message(alias);
      const answered = alias === ALIAS_B
        ? [inbound, message(alias, {
          id: `${alias}-outbound`,
          eventId: `${alias}-outbound-event`,
          direction: 'OUTBOUND',
          senderType: 'URBA',
          text: 'Resposta do segundo',
        })]
        : [inbound];
      return jsonResponse({ contactId: `poc:${alias}`, conversation: {}, messages: answered });
    }));

    render(<App />);

    expect(await screen.findByLabelText('Não lidas')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Primeiro' })).toBeInTheDocument();
    expect(screen.getAllByLabelText('Não lidas')).toHaveLength(1);
    await userEvent.setup().click(await screen.findByRole('button', { name: /segundo/i }));
    expect(await screen.findByText('Resposta do segundo')).toBeInTheDocument();
  });
});
