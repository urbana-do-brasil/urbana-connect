import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { createContact, saveUiState } from './state/contactStore';
import { jsonResponse } from './test/httpTestUtils';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
const inbound = {
  id: 'inbound-1',
  eventId: 'ui-22222222-2222-4222-8222-222222222222',
  correlationId: 'correlation-1',
  contactId: `poc:${ALIAS}`,
  direction: 'INBOUND',
  senderType: 'CONTACT',
  type: 'TEXT',
  text: 'Mensagem persistida',
  createdAt: '2026-08-06T12:00:00.000Z',
};

describe('App reload contract', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('restores the canonical projection and archives without DELETE remoto', async () => {
    const contact = createContact('Histórico', () => '11111111-1111-4111-8111-111111111111');
    saveUiState({ schemaVersion: 1, contacts: [{ ...contact, contactAlias: ALIAS }], activeContactAlias: ALIAS });
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      contactId: `poc:${ALIAS}`,
      conversation: { mode: 'AI' },
      messages: [inbound, {
        ...inbound,
        id: 'outbound-1',
        eventId: 'outbound-event-1',
        direction: 'OUTBOUND',
        senderType: 'URBA',
        text: 'Resposta persistida',
      }],
    }));
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('confirm', vi.fn(() => true));

    render(<App />);

    expect(await screen.findByText('Resposta persistida')).toBeInTheDocument();
    const deleteCalls = fetchMock.mock.calls.filter(([, init]) => init?.method === 'DELETE');
    expect(deleteCalls).toHaveLength(0);

    await userEvent.setup().click(screen.getByRole('button', { name: /ocultar contato/i }));
    await waitFor(() => expect(screen.getByText(/crie um contato local/i)).toBeInTheDocument());
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
  });
});
