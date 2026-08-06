import {
  conversationReducer,
  createConversationState,
  getVisibleMessages,
  hasPendingWork,
  type PendingSend,
} from './conversationReducer';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
const CONTACT_ID = `poc:${ALIAS}`;
const NOW = '2026-08-06T12:00:00.000Z';

function message(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'message-1',
    eventId: 'ui-22222222-2222-4222-8222-222222222222',
    correlationId: 'corr-1',
    contactId: CONTACT_ID,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: 'Oi',
    createdAt: NOW,
    ...overrides,
  };
}

function projection(messages: unknown[], mode?: 'AI' | 'HUMAN') {
  return {
    contactId: CONTACT_ID,
    conversation: mode ? { mode } : {},
    messages,
  };
}

function pending(overrides: Partial<PendingSend> = {}): PendingSend {
  return {
    eventId: 'ui-22222222-2222-4222-8222-222222222222',
    contactAlias: ALIAS,
    text: 'Oi',
    occurredAt: NOW,
    attempts: 1,
    state: 'WAITING',
    lastError: null,
    correlationId: null,
    ...overrides,
  };
}

describe('conversationReducer', () => {
  it('shows a validated optimistic inbound message immediately', () => {
    const state = conversationReducer(createConversationState(), {
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: pending({ state: 'ACCEPTING' }),
    });

    expect(getVisibleMessages(state[ALIAS])).toEqual([
      expect.objectContaining({
        id: `optimistic-${pending().eventId}`,
        direction: 'INBOUND',
        senderType: 'CONTACT',
        text: 'Oi',
      }),
    ]);
    expect(state[ALIAS].processingState).toBe('WAITING');
  });

  it('deduplicates by canonical id, falls back to event/direction/correlation, orders, and reconciles optimism', () => {
    const optimistic = pending();
    const current = conversationReducer(createConversationState(), {
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: optimistic,
    });
    const next = conversationReducer(current, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([
        message({ id: 'out-1', eventId: 'evt-out', direction: 'OUTBOUND', senderType: 'URBA', text: 'Resposta', createdAt: '2026-08-06T12:00:02Z' }),
        message({ id: 'in-1', createdAt: '2026-08-06T12:00:01Z' }),
        message({ id: 'in-1', createdAt: '2026-08-06T12:00:01Z' }),
        message({ id: 'duplicate-no-id', eventId: optimistic.eventId, direction: 'INBOUND', correlationId: 'corr-1' }),
      ]),
    });

    expect(next[ALIAS].messages.map((item) => item.id)).toEqual(['in-1', 'out-1']);
    expect(next[ALIAS].optimisticMessages).toHaveLength(0);
    expect(getVisibleMessages(next[ALIAS]).map((item) => item.text)).toEqual(['Oi', 'Resposta']);
    expect(next[ALIAS].processingState).toBe('IDLE');
  });

  it('keeps waiting for a canonical output, but ends the indicator in HUMAN mode', () => {
    const waiting = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()]),
    });
    expect(waiting[ALIAS].processingState).toBe('WAITING');
    expect(hasPendingWork(waiting[ALIAS])).toBe(true);

    const human = conversationReducer(waiting, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()], 'HUMAN'),
    });
    expect(human[ALIAS].processingState).toBe('HUMAN');
    expect(hasPendingWork(human[ALIAS])).toBe(false);
    expect(getVisibleMessages(human[ALIAS])).toHaveLength(1);
  });

  it('marks only an inactive conversation as unread when a new canonical output arrives', () => {
    const initial = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()]),
      activeAlias: 'manual-33333333-3333-4333-8333-333333333333',
    });
    const next = conversationReducer(initial, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([
        message(),
        message({ id: 'out-1', eventId: 'out', direction: 'OUTBOUND', senderType: 'URBA', text: 'Olá', createdAt: '2026-08-06T12:00:03Z' }),
      ]),
      activeAlias: 'manual-33333333-3333-4333-8333-333333333333',
    });
    expect(next[ALIAS].unread).toBe(true);

    const active = conversationReducer(next, {
      type: 'MARK_READ',
      alias: ALIAS,
    });
    expect(active[ALIAS].unread).toBe(false);
  });

  it('never turns a technical failure into a URBA message and preserves retryable originals', () => {
    const started = conversationReducer(createConversationState(), {
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: pending({ state: 'ACCEPTING' }),
    });
    const failed = conversationReducer(started, {
      type: 'SEND_FAILED',
      alias: ALIAS,
      eventId: pending().eventId,
      error: 'Não foi possível acompanhar a mensagem.',
      retryable: true,
    });

    expect(failed[ALIAS].processingState).toBe('FAILED_RETRYABLE');
    expect(getVisibleMessages(failed[ALIAS])).toHaveLength(1);
    expect(getVisibleMessages(failed[ALIAS])[0]?.senderType).toBe('CONTACT');
    expect(getVisibleMessages(failed[ALIAS]).some((item) => item.senderType === 'URBA')).toBe(false);
  });
});
