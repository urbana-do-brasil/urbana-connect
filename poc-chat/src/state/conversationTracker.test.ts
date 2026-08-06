import { ConversationHttpError } from '../api/conversationClient';
import type { ConversationApi } from '../api/conversationClient';
import {
  conversationReducer,
  createConversationState,
  hasPendingWork,
  type ConversationAction,
  type ConversationState,
} from './conversationReducer';
import { ConversationTracker } from './conversationTracker';

const ALIAS_A = 'manual-11111111-1111-4111-8111-111111111111';
const ALIAS_B = 'manual-22222222-2222-4222-8222-222222222222';
const EVENT_ID = 'ui-33333333-3333-4333-8333-333333333333';
const NOW = '2026-08-06T12:00:00.000Z';

function projection(alias: string, answered = false) {
  const contactId = `poc:${alias}`;
  const inbound = {
    id: `${alias}-in`,
    eventId: EVENT_ID,
    correlationId: `${alias}-corr`,
    contactId,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: alias,
    createdAt: NOW,
  };
  return {
    contactId,
    conversation: {},
    messages: answered ? [inbound, {
      ...inbound,
      id: `${alias}-out`,
      eventId: `${alias}-out-event`,
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: `Resposta ${alias}`,
    }] : [inbound],
  };
}

function harness(api: ConversationApi, intervalMs = 100) {
  let state: ConversationState = createConversationState();
  const actions: ConversationAction[] = [];
  const dispatch = (action: ConversationAction) => {
    actions.push(action);
    state = conversationReducer(state, action);
  };
  const tracker = new ConversationTracker({
    api,
    dispatch,
    getConversationState: (alias) => state[alias],
    getActiveAlias: () => ALIAS_A,
    intervalMs,
    maxWaitMs: 1_000,
  });
  return { tracker, actions, dispatch, getState: () => state };
}

describe('ConversationTracker', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('retries uncertain transport at most once with the same eventId', async () => {
    const api: ConversationApi = {
      sendTextMessage: vi.fn()
        .mockRejectedValueOnce(new ConversationHttpError('upstream unavailable', { status: 504, retryable: true }))
        .mockResolvedValueOnce({ eventId: EVENT_ID, correlationId: 'corr-1', status: 'QUEUED' }),
      getConversationProjection: vi.fn().mockResolvedValue(projection(ALIAS_A)),
    };
    const { tracker, actions } = harness(api);

    await tracker.send(ALIAS_A, 'Oi', NOW, EVENT_ID);

    expect(api.sendTextMessage).toHaveBeenCalledTimes(2);
    expect(api.sendTextMessage).toHaveBeenNthCalledWith(1, ALIAS_A, expect.objectContaining({ eventId: EVENT_ID }));
    expect(api.sendTextMessage).toHaveBeenNthCalledWith(2, ALIAS_A, expect.objectContaining({ eventId: EVENT_ID }));
    expect(actions.filter((action) => action.type === 'RETRY_STARTED')).toHaveLength(1);

    await tracker.retry(ALIAS_A, EVENT_ID);
    expect(api.sendTextMessage).toHaveBeenCalledTimes(3);
    expect(api.sendTextMessage).toHaveBeenLastCalledWith(ALIAS_A, expect.objectContaining({ eventId: EVENT_ID }));
    tracker.dispose();
  });

  it('polls only pending contacts concurrently and stops each tracker after canonical output', async () => {
    const api: ConversationApi = {
      sendTextMessage: vi.fn(),
      getConversationProjection: vi.fn()
        .mockResolvedValueOnce(projection(ALIAS_A, true))
        .mockResolvedValueOnce(projection(ALIAS_B, true)),
    };
    const primary = harness(api);
    const { tracker, getState } = primary;
    const idleApi: ConversationApi = {
      sendTextMessage: vi.fn(),
      getConversationProjection: vi.fn(),
    };
    const idleHarness = harness(idleApi);

    const seed = (harnessValue: ReturnType<typeof harness>, alias: string) => {
      harnessValue.dispatch({ type: 'PROJECTION_RECEIVED', alias, projection: projection(alias) });
    };
    seed(primary, ALIAS_A);
    seed(primary, ALIAS_B);

    await tracker.startPolling(ALIAS_A);
    await tracker.startPolling(ALIAS_B);
    await idleHarness.tracker.startPolling(ALIAS_A);
    await vi.runOnlyPendingTimersAsync();

    expect(api.getConversationProjection).toHaveBeenCalledWith(ALIAS_A);
    expect(api.getConversationProjection).toHaveBeenCalledWith(ALIAS_B);
    expect(idleApi.getConversationProjection).not.toHaveBeenCalled();
    expect(hasPendingWork(getState()[ALIAS_A])).toBe(false);
    expect(hasPendingWork(getState()[ALIAS_B])).toBe(false);
    tracker.dispose();
    idleHarness.tracker.dispose();
  });

  it('treats a 409 receipt as follow-up state and does not create a second event', async () => {
    const api: ConversationApi = {
      sendTextMessage: vi.fn().mockResolvedValue({ eventId: EVENT_ID, correlationId: 'corr-1', status: 'DUPLICATE' }),
      getConversationProjection: vi.fn().mockResolvedValue(projection(ALIAS_A, true)),
    };
    const { tracker, actions } = harness(api);

    await tracker.send(ALIAS_A, 'Oi', NOW, EVENT_ID);
    await vi.runOnlyPendingTimersAsync();

    expect(api.sendTextMessage).toHaveBeenCalledTimes(1);
    expect(actions.some((action) => action.type === 'RECEIPT_RECEIVED')).toBe(true);
    tracker.dispose();
  });
});
