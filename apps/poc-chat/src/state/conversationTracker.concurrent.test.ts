import type { ConversationApi } from '../api/conversationClient';
import { conversationReducer, createConversationState, type ConversationState } from './conversationReducer';
import { ConversationTracker } from './conversationTracker';

const ALIAS_A = 'manual-11111111-1111-4111-8111-111111111111';
const ALIAS_B = 'manual-22222222-2222-4222-8222-222222222222';

function answered(alias: string) {
  const inbound = {
    id: `${alias}-in`,
    eventId: `${alias}-event`,
    correlationId: `${alias}-corr`,
    contactId: `poc:${alias}`,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: `Mensagem ${alias}`,
    createdAt: '2026-08-06T12:00:00.000Z',
  };
  return {
    contactId: `poc:${alias}`,
    conversation: {},
    messages: [inbound, {
      ...inbound,
      id: `${alias}-out`,
      eventId: `${alias}-out-event`,
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: `Resposta ${alias}`,
    }],
  };
}

describe('ConversationTracker concurrent lifecycle', () => {
  it('keeps independent aliases in the correct state and disposes timers', async () => {
    let state: ConversationState = createConversationState();
    const dispatch = (action: Parameters<typeof conversationReducer>[1]) => {
      state = conversationReducer(state, action);
    };
    const api: ConversationApi = {
      sendTextMessage: vi.fn(),
      getConversationProjection: vi.fn()
        .mockResolvedValueOnce(answered(ALIAS_A))
        .mockResolvedValueOnce(answered(ALIAS_B)),
    };
    const tracker = new ConversationTracker({
      api,
      dispatch,
      getConversationState: (alias) => state[alias],
      getActiveAlias: () => ALIAS_A,
      intervalMs: 10,
    });
    dispatch({ type: 'SEND_STARTED', alias: ALIAS_A, pending: {
      eventId: `${ALIAS_A}-event`, contactAlias: ALIAS_A, text: 'A',
      occurredAt: '2026-08-06T12:00:00.000Z', attempts: 1, state: 'WAITING',
      lastError: null, correlationId: null,
    } });
    dispatch({ type: 'SEND_STARTED', alias: ALIAS_B, pending: {
      eventId: `${ALIAS_B}-event`, contactAlias: ALIAS_B, text: 'B',
      occurredAt: '2026-08-06T12:00:00.000Z', attempts: 1, state: 'WAITING',
      lastError: null, correlationId: null,
    } });

    await Promise.all([
      tracker.startPolling(ALIAS_A, true),
      tracker.startPolling(ALIAS_B, true),
    ]);

    expect(state[ALIAS_A]?.messages[1]?.text).toBe(`Resposta ${ALIAS_A}`);
    expect(state[ALIAS_B]?.messages[1]?.text).toBe(`Resposta ${ALIAS_B}`);
    const callCount = vi.mocked(api.getConversationProjection).mock.calls.length;
    tracker.dispose();
    await vi.waitFor(() => expect(vi.mocked(api.getConversationProjection)).toHaveBeenCalledTimes(callCount));
  });
});
