import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

const firstAlias = 'manual-11111111-1111-4111-8111-111111111111';
const secondAlias = 'manual-22222222-2222-4222-8222-222222222222';

function projection(alias: string, text: string, answered = true) {
  const contactId = `poc:${alias}`;
  const inbound = {
    id: `${alias}-in`,
    eventId: `ui-${alias.slice(7)}`,
    correlationId: `${alias}-corr`,
    contactId,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text,
    createdAt: '2026-08-06T12:00:00.000Z',
  };
  return {
    contactId,
    conversation: {},
    messages: answered ? [inbound, {
      ...inbound,
      id: `${alias}-out`,
      eventId: `${alias}-out`,
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: `Resposta para ${text}`,
    }] : [inbound],
  };
}

describe('App multi-contact behavior', () => {
  it('keeps concurrent responses in their own conversations and marks inactive replies unread', async () => {
    const user = userEvent.setup();
    const projections = new Map<string, unknown>();
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      const match = path.match(/conversations\/(manual-[a-f0-9-]{36})/);
      const alias = match?.[1] ?? firstAlias;
      if (init?.method === 'POST') {
        const payload = JSON.parse(String(init.body)) as { eventId: string; text: string };
        projections.set(alias, projection(alias, payload.text, false));
        return new Response(JSON.stringify({ eventId: payload.eventId, correlationId: `${alias}-corr`, status: 'QUEUED' }), { status: 202 });
      }
      return new Response(JSON.stringify(projections.get(alias) ?? projection(alias, '')));
    });
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('crypto', { randomUUID: vi.fn()
      .mockReturnValueOnce(firstAlias.slice(7))
      .mockReturnValueOnce(secondAlias.slice(7))
      .mockReturnValue('33333333-3333-4333-8333-333333333333') });
    vi.spyOn(window, 'localStorage', 'get').mockReturnValue(localStorage);
    localStorage.clear();

    render(<App />);
    await user.type(screen.getByRole('textbox', { name: /nome do contato/i }), 'Primeiro');
    await user.click(screen.getByRole('button', { name: /criar contato/i }));
    await user.type(screen.getByRole('textbox', { name: /mensagem/i }), 'A');
    await user.keyboard('{Enter}');
    await user.type(screen.getByRole('textbox', { name: /nome do contato/i }), 'Segundo');
    await user.click(screen.getByRole('button', { name: /criar contato/i }));
    await user.type(screen.getByRole('textbox', { name: /mensagem/i }), 'B');
    await user.keyboard('{Enter}');

    projections.set(firstAlias, projection(firstAlias, 'A', true));
    await waitFor(() => expect(screen.getByLabelText(/não lidas/i)).toBeInTheDocument(), { timeout: 10_000 });
    await user.click(screen.getAllByRole('button', { name: /primeiro/i })[0]);
    expect(screen.getByText('Resposta para A')).toBeInTheDocument();
    expect(screen.queryByText('Resposta para B')).not.toBeInTheDocument();
  });
});
