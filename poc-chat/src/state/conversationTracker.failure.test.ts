import type { ConversationApi } from '../api/conversationClient';
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

function projection(answered: boolean) {
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
  };
}

describe('ConversationTracker failure handling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps waiting for a real response beyond thirty seconds', async () => {
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
        return projection(pollCount >= 40);
      }),
    };
    const tracker = new ConversationTracker({
      api,
      dispatch,
      getConversationState: (alias) => state[alias],
      getActiveAlias: () => ALIAS,
      intervalMs: 1_000,
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
    await vi.advanceTimersByTimeAsync(39_000);

    expect(state[ALIAS]?.processingState).toBe('IDLE');
    expect(state[ALIAS]?.messages).toEqual([
      expect.objectContaining({ text: 'Oi' }),
      expect.objectContaining({ text: 'Resposta canônica', senderType: 'URBA' }),
    ]);
    expect(actions.some((action) => action.type === 'SYNC_FAILED')).toBe(false);
    tracker.dispose();
  });
});
