import type { ConversationApi } from '../api/conversationClient';
import type { TurnSummary } from '../api/contracts';
import { turnSummary } from '../test/fixtures';
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

function projection(alias: string, answered = false, turn: TurnSummary | null = null) {
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
    turn,
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

  it('does not retry an ambiguous transport failure automatically', async () => {
    const api: ConversationApi = {
      sendTextMessage: vi.fn()
        .mockRejectedValueOnce(new Error('upstream unavailable')),
      getConversationProjection: vi.fn().mockResolvedValue(
        projection(ALIAS_A, false, turnSummary({ status: 'RECONCILING', correlationId: `${ALIAS_A}-corr` })),
      ),
    };
    const { tracker, actions, getState } = harness(api);

    await expect(tracker.send(ALIAS_A, 'Oi', NOW, EVENT_ID)).resolves.toBeNull();

    expect(api.sendTextMessage).toHaveBeenCalledTimes(1);
    expect(api.sendTextMessage).toHaveBeenNthCalledWith(1, ALIAS_A, expect.objectContaining({ eventId: EVENT_ID }));
    expect(actions.filter((action) => action.type === 'RETRY_STARTED')).toHaveLength(0);
    expect(getState()[ALIAS_A]?.processingState).toBe('RECONCILING');
    tracker.dispose();
  });

  it('retries only when the canonical summary explicitly allows it and preserves eventId', async () => {
    const api: ConversationApi = {
      sendTextMessage: vi.fn().mockResolvedValue({ eventId: EVENT_ID, correlationId: 'corr-1', status: 'QUEUED' }),
      getConversationProjection: vi.fn().mockResolvedValue(projection(
        ALIAS_A,
        false,
        turnSummary({ status: 'RUNNING', correlationId: 'corr-1' }),
      )),
    };
    const { tracker, dispatch, getState, actions } = harness(api);
    dispatch({
      type: 'SEND_STARTED',
      alias: ALIAS_A,
      pending: {
        eventId: EVENT_ID,
        contactAlias: ALIAS_A,
        text: 'Oi',
        occurredAt: NOW,
        attempts: 1,
        state: 'WAITING',
        lastError: null,
        correlationId: 'corr-1',
      },
    });
    dispatch({
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS_A,
      projection: projection(ALIAS_A, false, turnSummary({
        status: 'FAILED_SAFE_TO_RETRY',
        retryAllowed: true,
        correlationId: `${ALIAS_A}-corr`,
      })),
    });

    await tracker.retry(ALIAS_A, EVENT_ID);

    expect(api.sendTextMessage).toHaveBeenCalledWith(ALIAS_A, expect.objectContaining({ eventId: EVENT_ID }));
    expect(actions.filter((action) => action.type === 'RETRY_STARTED')).toHaveLength(1);
    expect(getState()[ALIAS_A]?.optimisticMessages[0]?.eventId).toBe(EVENT_ID);
    tracker.dispose();
  });

  it('continues from a stale completed turn to the second response with one POST', async () => {
    const firstInbound = {
      id: 'inbound-first',
      eventId: 'ui-first-22222222-2222-4222-8222-222222222222',
      correlationId: 'corr-first',
      contactId: `poc:${ALIAS_A}`,
      direction: 'INBOUND',
      senderType: 'CONTACT',
      type: 'TEXT',
      text: 'Primeira mensagem',
      createdAt: NOW,
    };
    const firstOutbound = {
      ...firstInbound,
      id: 'outbound-first',
      eventId: 'outbound-first-event',
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: 'Primeira resposta',
      createdAt: '2026-08-06T12:00:02.000Z',
    };
    const secondInbound = {
      ...firstInbound,
      id: 'inbound-second',
      eventId: EVENT_ID,
      correlationId: 'corr-second',
      text: 'Segunda mensagem',
      createdAt: '2026-08-06T12:00:04.000Z',
    };
    const secondOutbound = {
      ...secondInbound,
      id: 'outbound-second',
      eventId: 'outbound-second-event',
      direction: 'OUTBOUND',
      senderType: 'URBA',
      text: 'Segunda resposta',
      createdAt: '2026-08-06T12:00:06.000Z',
    };
    const firstCompleted = turnSummary({
      status: 'COMPLETED',
      correlationId: 'corr-first',
      finishedAt: '2026-08-06T12:00:03.000Z',
    });
    const secondCompleted = turnSummary({
      status: 'COMPLETED',
      correlationId: 'corr-second',
      finishedAt: '2026-08-06T12:00:07.000Z',
    });
    const staleProjection = {
      contactId: `poc:${ALIAS_A}`,
      conversation: {},
      messages: [firstInbound, firstOutbound],
      turn: firstCompleted,
    };
    const completedProjection = {
      contactId: `poc:${ALIAS_A}`,
      conversation: {},
      messages: [firstInbound, firstOutbound, secondInbound, secondOutbound],
      turn: secondCompleted,
    };
    const api: ConversationApi = {
      sendTextMessage: vi.fn().mockResolvedValue({
        eventId: EVENT_ID,
        correlationId: 'corr-second',
        status: 'QUEUED',
      }),
      getConversationProjection: vi.fn()
        .mockResolvedValueOnce(staleProjection)
        .mockResolvedValueOnce(completedProjection),
    };
    const { tracker, actions, getState } = harness(api);

    await tracker.send(ALIAS_A, 'Segunda mensagem', NOW, EVENT_ID);
    expect(actions).toEqual(expect.arrayContaining([
      expect.objectContaining({ type: 'SEND_STARTED', alias: ALIAS_A }),
      expect.objectContaining({
        type: 'RECEIPT_RECEIVED',
        alias: ALIAS_A,
        eventId: EVENT_ID,
        correlationId: 'corr-second',
        status: 'QUEUED',
      }),
    ]));
    expect(api.getConversationProjection).toHaveBeenCalledTimes(1);
    expect(getState()[ALIAS_A]?.turn?.correlationId).toBe('corr-first');
    expect(hasPendingWork(getState()[ALIAS_A])).toBe(true);

    await vi.advanceTimersByTimeAsync(100);

    expect(api.getConversationProjection).toHaveBeenCalledTimes(2);
    expect(getState()[ALIAS_A]?.messages).toEqual(expect.arrayContaining([
      expect.objectContaining({ text: 'Segunda resposta', senderType: 'URBA' }),
    ]));
    expect(getState()[ALIAS_A]?.processingState).toBe('IDLE');
    expect(hasPendingWork(getState()[ALIAS_A])).toBe(false);
    expect(api.sendTextMessage).toHaveBeenCalledTimes(1);
    expect(api.sendTextMessage).toHaveBeenCalledWith(ALIAS_A, expect.objectContaining({
      eventId: EVENT_ID,
      text: 'Segunda mensagem',
    }));

    await vi.advanceTimersByTimeAsync(1_000);
    expect(api.getConversationProjection).toHaveBeenCalledTimes(2);
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

  it('does not send automation after the canonical projection assigns HUMAN ownership', async () => {
    const api: ConversationApi = {
      sendTextMessage: vi.fn(),
      getConversationProjection: vi.fn().mockResolvedValue({
        contactId: `poc:${ALIAS_A}`,
        ownership: 'HUMAN',
        resumeStatus: 'SYNCHRONIZING',
        conversation: { mode: 'HUMAN', version: 1 },
        messages: [],
        turn: null,
      }),
    };
    const { tracker } = harness(api);

    await tracker.sync(ALIAS_A);
    const receipt = await tracker.send(ALIAS_A, 'Não deve ser enviado');

    expect(receipt).toBeNull();
    expect(api.sendTextMessage).not.toHaveBeenCalled();
    tracker.dispose();
  });
});
