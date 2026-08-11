import {
  parseConversationProjection,
  parseTurnReceipt,
  type TurnSummary,
} from './contracts';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
const CONTACT_ID = `poc:${ALIAS}`;
const EVENT_ID = 'ui-22222222-2222-4222-8222-222222222222';

function safeTurn(overrides: Partial<TurnSummary> = {}): TurnSummary {
  return {
    status: 'RUNNING',
    correlationId: 'correlation-1',
    attempt: 1,
    retryAllowed: false,
    failureClass: null,
    acceptedAt: '2026-08-07T12:00:00.000Z',
    startedAt: '2026-08-07T12:00:04.000Z',
    finishedAt: null,
    ...overrides,
  };
}

describe('safe conversation contract', () => {
  it.each(['QUEUED', 'RUNNING', 'DELAYED', 'RECONCILING', 'COMPLETED', 'FAILED_SAFE_TO_RETRY', 'FAILED_TERMINAL', 'BLOCKED_BY_HUMAN'] as const)(
    'parses the canonical %s turn summary', (status) => {
      const parsed = parseConversationProjection({
        contactId: CONTACT_ID,
        conversation: {},
        messages: [],
        turn: safeTurn({ status }),
      }, CONTACT_ID);

      expect(parsed.turn?.status).toBe(status);
      expect(parsed.turn?.retryAllowed).toBe(false);
    },
  );

  it('returns only safe turn fields and never exposes backend secrets', () => {
    const parsed = parseConversationProjection({
      contactId: CONTACT_ID,
      conversation: {},
      messages: [],
      turn: {
        ...safeTurn({ status: 'FAILED_SAFE_TO_RETRY', retryAllowed: true, failureClass: 'UPSTREAM_UNAVAILABLE' }),
        hermesSessionId: 'secret-session',
        claimToken: 'secret-claim',
        prompt: 'secret prompt',
        rawException: 'secret exception',
      },
    }, CONTACT_ID);

    expect(parsed.turn).toEqual(safeTurn({
      status: 'FAILED_SAFE_TO_RETRY',
      retryAllowed: true,
      failureClass: 'UPSTREAM_UNAVAILABLE',
    }));
    expect(JSON.stringify(parsed)).not.toMatch(/secret-session|secret-claim|secret prompt|secret exception/i);
  });

  it('accepts a new-contact projection without a turn summary', () => {
    expect(parseConversationProjection({
      contactId: CONTACT_ID,
      conversation: {},
      messages: [],
      turn: null,
    }, CONTACT_ID).turn).toBeNull();
  });

  it('keeps the event identity when parsing an accepted receipt', () => {
    expect(parseTurnReceipt({
      eventId: EVENT_ID,
      correlationId: 'correlation-1',
      status: 'QUEUED',
      output: null,
      error: null,
    }, EVENT_ID).eventId).toBe(EVENT_ID);
  });
});
