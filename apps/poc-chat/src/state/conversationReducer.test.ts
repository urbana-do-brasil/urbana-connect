import {
  conversationReducer,
  createConversationState,
  getVisibleMessages,
  hasPendingWork,
  type PendingSend,
} from './conversationReducer';
import type { TurnSummary } from '../api/contracts';
import { turnSummary } from '../test/fixtures';

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

function projection(messages: unknown[], mode?: 'AI' | 'HUMAN', turn: TurnSummary | null = null) {
  return {
    contactId: CONTACT_ID,
    conversation: mode ? { mode } : {},
    messages,
    turn,
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

  it('distinguishes delayed and reconciling turns while keeping them pollable', () => {
    const delayed = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()], undefined, turnSummary({ status: 'DELAYED' })),
    });
    expect(delayed[ALIAS].processingState).toBe('DELAYED');
    expect(hasPendingWork(delayed[ALIAS])).toBe(true);

    const reconciling = conversationReducer(delayed, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()], undefined, turnSummary({ status: 'RECONCILING' })),
    });
    expect(reconciling[ALIAS].processingState).toBe('RECONCILING');
    expect(hasPendingWork(reconciling[ALIAS])).toBe(true);
  });

  it('treats a projection GET failure as synchronization loss, not as retry authorization', () => {
    const delayed = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()], undefined, turnSummary({ status: 'DELAYED' })),
    });
    const failedSync = conversationReducer(delayed, {
      type: 'SYNC_FAILED',
      alias: ALIAS,
      error: 'Falha temporária de leitura',
    });

    expect(failedSync[ALIAS].processingState).toBe('DELAYED');
    expect(failedSync[ALIAS].turn?.status).toBe('DELAYED');
    expect(failedSync[ALIAS].optimisticMessages.every((item) => item.state !== 'FAILED_SAFE_TO_RETRY')).toBe(true);
    expect(hasPendingWork(failedSync[ALIAS])).toBe(true);
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

  it('never turns an ambiguous send failure into a URBA message or local retry', () => {
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
      retryable: false,
    });

    expect(failed[ALIAS].processingState).toBe('WAITING');
    expect(getVisibleMessages(failed[ALIAS])).toHaveLength(1);
    expect(getVisibleMessages(failed[ALIAS])[0]?.senderType).toBe('CONTACT');
    expect(getVisibleMessages(failed[ALIAS]).some((item) => item.senderType === 'URBA')).toBe(false);
  });

  it('enables retry only for the backend-authorized terminal summary', () => {
    const started = conversationReducer(createConversationState(), {
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: pending({ state: 'ACCEPTING', correlationId: 'corr-1' }),
    });
    const safe = conversationReducer(started, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([message()], undefined, turnSummary({
        status: 'FAILED_SAFE_TO_RETRY',
        retryAllowed: true,
        correlationId: 'corr-1',
      })),
    });

    expect(safe[ALIAS].processingState).toBe('FAILED_SAFE_TO_RETRY');
    expect(safe[ALIAS].optimisticMessages[0]?.state).toBe('FAILED_SAFE_TO_RETRY');
    expect(hasPendingWork(safe[ALIAS])).toBe(false);
    expect(getVisibleMessages(safe[ALIAS]).some((item) => item.senderType === 'URBA')).toBe(false);
  });

  it('keeps an optimistic second turn pollable after a stale completed first turn', () => {
    const firstInbound = message({
      id: 'inbound-first',
      eventId: 'ui-first-22222222-2222-4222-8222-222222222222',
      correlationId: 'corr-first',
      text: 'Primeira mensagem',
    });
    const firstOutbound = message({
      id: 'outbound-first',
      eventId: 'ui-first-response-22222222-2222-4222-8222-222222222222',
      correlationId: 'corr-first',
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: 'Primeira resposta',
      createdAt: '2026-08-06T12:00:02.000Z',
    });
    const second = pending({
      eventId: 'ui-second-22222222-2222-4222-8222-222222222222',
      text: 'Segunda mensagem',
      state: 'ACCEPTING',
    });
    const firstCompleted = turnSummary({
      status: 'COMPLETED',
      correlationId: 'corr-first',
      finishedAt: '2026-08-06T12:00:03.000Z',
    });

    let state = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([firstInbound, firstOutbound], undefined, firstCompleted),
    });
    expect(hasPendingWork(state[ALIAS])).toBe(false);

    state = conversationReducer(state, {
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: second,
    });
    state = conversationReducer(state, {
      type: 'RECEIPT_RECEIVED',
      alias: ALIAS,
      eventId: second.eventId,
      correlationId: 'corr-second',
      status: 'QUEUED',
    });

    const stale = conversationReducer(state, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection([firstInbound, firstOutbound], undefined, firstCompleted),
    });

    expect(stale[ALIAS].turn?.correlationId).toBe('corr-first');
    expect(stale[ALIAS].optimisticMessages).toEqual([
      expect.objectContaining({
        eventId: second.eventId,
        correlationId: 'corr-second',
        state: 'WAITING',
      }),
    ]);
    expect(stale[ALIAS].processingState).toBe('WAITING');
    expect(hasPendingWork(stale[ALIAS])).toBe(true);

    const secondInbound = message({
      id: 'inbound-second',
      eventId: second.eventId,
      correlationId: 'corr-second',
      text: second.text,
      createdAt: '2026-08-06T12:00:04.000Z',
    });
    const secondOutbound = message({
      id: 'outbound-second',
      eventId: 'ui-second-response-22222222-2222-4222-8222-222222222222',
      correlationId: 'corr-second',
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: 'Segunda resposta',
      createdAt: '2026-08-06T12:00:06.000Z',
    });
    const completedSecond = conversationReducer(stale, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: projection(
        [firstInbound, firstOutbound, secondInbound, secondOutbound],
        undefined,
        turnSummary({
          status: 'COMPLETED',
          correlationId: 'corr-second',
          finishedAt: '2026-08-06T12:00:07.000Z',
        }),
      ),
    });

    expect(completedSecond[ALIAS].optimisticMessages).toHaveLength(0);
    expect(getVisibleMessages(completedSecond[ALIAS]).map((item) => item.text)).toEqual([
      'Primeira mensagem',
      'Primeira resposta',
      'Segunda mensagem',
      'Segunda resposta',
    ]);
    expect(completedSecond[ALIAS].processingState).toBe('IDLE');
    expect(hasPendingWork(completedSecond[ALIAS])).toBe(false);
  });
});
