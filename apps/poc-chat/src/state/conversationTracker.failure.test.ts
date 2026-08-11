import type { ConversationApi } from '../api/conversationClient';
import { ConversationHttpError } from '../api/conversationClient';
import { turnSummary } from '../test/fixtures';
import {
  conversationReducer,
  createConversationState,
  type ConversationAction,
  type ConversationState,
} from './conversationReducer';
import { ConversationTracker } from './conversationTracker';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
const EVENT_ID = 'ui-22222222-2222-4222-8222-222222222222';
const NOW = '2026-08-06T12:00:00.000Z';

function projection(status: 'DELAYED' | 'COMPLETED', answered: boolean) {
  const inbound = {
    id: 'inbound-1',
    eventId: EVENT_ID,
    correlationId: 'corr-1',
    contactId: `poc:${ALIAS}`,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: 'Oi',
    createdAt: NOW,
  };
  return {
    contactId: `poc:${ALIAS}`,
    conversation: {},
    messages: answered
      ? [inbound, {
        ...inbound,
        id: 'outbound-1',
        eventId: 'outbound-event-1',
        direction: 'OUTBOUND',
        senderType: 'URBA',
        text: 'Resposta canônica',
      }]
      : [inbound],
    turn: turnSummary({
      status,
      correlationId: 'corr-1',
      finishedAt: status === 'COMPLETED' ? '2026-08-07T12:02:03.000Z' : null,
    }),
  };
}

describe('ConversationTracker failure handling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps polling beyond 120 seconds until a delayed turn is canonical', async () => {
    let state: ConversationState = createConversationState();
    const actions: ConversationAction[] = [];
    const dispatch = (action: ConversationAction) => {
      actions.push(action);
      state = conversationReducer(state, action);
    };
    let pollCount = 0;
    const api: ConversationApi = {
      sendTextMessage: vi.fn(),
      getConversationProjection: vi.fn().mockImplementation(async () => {
        pollCount += 1;
        const completed = pollCount >= 122;
        return projection(completed ? 'COMPLETED' : 'DELAYED', completed);
      }),
    };
    const tracker = new ConversationTracker({
      api,
      dispatch,
      getConversationState: (alias) => state[alias],
      getActiveAlias: () => ALIAS,
      intervalMs: 1_000,
      maxIntervalMs: 1_000,
      maxWaitMs: 120_000,
    });

    dispatch({
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: {
        eventId: EVENT_ID,
        contactAlias: ALIAS,
        text: 'Oi',
        occurredAt: NOW,
        attempts: 1,
        state: 'WAITING',
        lastError: null,
        correlationId: 'corr-1',
      },
    });

    await tracker.startPolling(ALIAS, true);
    await vi.advanceTimersByTimeAsync(121_000);

    expect(state[ALIAS]?.processingState).toBe('IDLE');
    expect(state[ALIAS]?.messages).toEqual([
      expect.objectContaining({ text: 'Oi' }),
      expect.objectContaining({ text: 'Resposta canônica', senderType: 'URBA' }),
    ]);
    expect(actions.some((action) => action.type === 'SYNC_FAILED')).toBe(false);
    tracker.dispose();
  });

  it('keeps the delayed turn under observation after a temporary GET error', async () => {
    let state: ConversationState = createConversationState();
    const actions: ConversationAction[] = [];
    const dispatch = (action: ConversationAction) => {
      actions.push(action);
      state = conversationReducer(state, action);
    };
    const api: ConversationApi = {
      sendTextMessage: vi.fn(),
      getConversationProjection: vi.fn()
        .mockResolvedValueOnce(projection('DELAYED', false))
        .mockRejectedValueOnce(new ConversationHttpError('GET indisponível', { status: 503, retryable: true }))
        .mockResolvedValueOnce(projection('COMPLETED', true)),
    };
    const tracker = new ConversationTracker({
      api,
      dispatch,
      getConversationState: (alias) => state[alias],
      getActiveAlias: () => ALIAS,
      intervalMs: 1_000,
      maxIntervalMs: 1_000,
    });

    dispatch({
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: {
        eventId: EVENT_ID,
        contactAlias: ALIAS,
        text: 'Oi',
        occurredAt: NOW,
        attempts: 1,
        state: 'WAITING',
        lastError: null,
        correlationId: 'corr-1',
      },
    });

    await tracker.startPolling(ALIAS, true);
    expect(state[ALIAS]?.processingState).toBe('DELAYED');

    await vi.advanceTimersByTimeAsync(1_000);
    expect(actions.at(-1)?.type).toBe('SYNC_FAILED');
    expect(state[ALIAS]?.processingState).toBe('DELAYED');
    expect(state[ALIAS]?.optimisticMessages[0]?.state).not.toBe('FAILED_SAFE_TO_RETRY');

    await vi.advanceTimersByTimeAsync(1_000);
    expect(state[ALIAS]?.messages).toEqual([
      expect.objectContaining({ text: 'Oi' }),
      expect.objectContaining({ text: 'Resposta canônica', senderType: 'URBA' }),
    ]);
    expect(vi.mocked(api.getConversationProjection)).toHaveBeenCalledTimes(3);
    tracker.dispose();
  });
});
