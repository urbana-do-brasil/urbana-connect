import { ConversationClient } from './conversationClient';
import { createContact, saveUiState, type PersistedUiState } from '../state/contactStore';

const alias = 'manual-11111111-1111-4111-8111-111111111111';
const displayName = 'Nome secreto que não pode sair do navegador';

function storage(): Storage {
  let value: string | null = null;
  return {
    getItem: () => value,
    setItem: (_key, next) => {
      value = next;
    },
    removeItem: () => {
      value = null;
    },
    clear: () => {
      value = null;
    },
    key: () => null,
    length: 0,
  };
}

describe('privacy contract', () => {
  it('never sends displayName in path, headers, or body; storage contains only visual metadata', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      eventId: 'ui-22222222-2222-4222-8222-222222222222',
      correlationId: 'corr-1',
      status: 'QUEUED',
    }), { status: 202 }));
    const client = new ConversationClient(fetchMock);
    const contact = createContact(displayName, () => alias.replace('manual-', ''));
    const localStorage = storage();
    const state: PersistedUiState = { schemaVersion: 1, contacts: [contact], activeContactAlias: alias };

    saveUiState(state, localStorage);
    await client.sendTextMessage(alias, {
      eventId: 'ui-22222222-2222-4222-8222-222222222222',
      type: 'TEXT',
      text: 'Mensagem permitida',
      occurredAt: '2026-08-06T12:00:00.000Z',
    });

    const requestText = JSON.stringify(fetchMock.mock.calls[0]);
    expect(requestText).not.toContain(displayName);
    const stored = localStorage.getItem('urbana.poc-chat.v1');
    expect(stored).toContain(displayName);
    expect(stored).not.toMatch(/transcript|token|eventId|correlationId|payload/i);
  });

  it('accepts only the safe turn summary and keeps retry authorization backend-owned', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      contactId: `poc:${alias}`,
      conversation: {},
      messages: [],
      turn: {
        status: 'FAILED_SAFE_TO_RETRY',
        correlationId: 'corr-1',
        attempt: 2,
        retryAllowed: true,
        failureClass: 'UPSTREAM_UNAVAILABLE',
        acceptedAt: '2026-08-06T12:00:00.000Z',
        startedAt: '2026-08-06T12:00:04.000Z',
        finishedAt: '2026-08-06T12:01:00.000Z',
        hermesSessionId: 'hidden-session',
        rawError: 'hidden-error',
      },
    }), { status: 200 }));
    const client = new ConversationClient(fetchMock);

    const projection = await client.getConversationProjection(alias);

    expect(projection.turn).toMatchObject({ status: 'FAILED_SAFE_TO_RETRY', retryAllowed: true });
    expect(JSON.stringify(projection)).not.toMatch(/hidden-session|hidden-error/i);
  });
});
