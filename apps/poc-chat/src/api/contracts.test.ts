import {
  parseHumanMessageReceipt,
  parseConversationProjection,
  parseResumeReceipt,
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

  it('accepts additive legacy ownership, resume and local-control fields without exposing internals', () => {
    const parsed = parseConversationProjection({
      contactId: CONTACT_ID,
      conversation: {
        mode: 'HUMAN',
        ownership: 'HUMAN',
        resume: {
          status: 'RECONCILING',
          retryAllowed: false,
          failureClass: 'HUMAN_CONTEXT_PENDING',
          hermesSessionId: 'secret-session',
          rawError: 'secret-error',
        },
      pocControls: {
          approvePaymentProof: true,
          recordDecision: false,
          returnToUrba: true,
          endpoint: '/internal/secret',
        },
        internalOwnerId: 'secret-owner',
      },
      messages: [],
      turn: null,
    }, CONTACT_ID);

    expect(parsed.conversation).toEqual({
      mode: 'HUMAN',
      ownership: 'HUMAN',
      resume: {
        status: 'RECONCILING',
        retryAllowed: false,
        failureClass: 'HUMAN_CONTEXT_PENDING',
      },
      pocControls: {
        approvePaymentProof: true,
        recordHumanMessage: false,
        returnToUrba: true,
      },
    });
    expect(JSON.stringify(parsed)).not.toMatch(/secret-session|secret-error|secret-owner|internal\/secret/i);
  });

  it('normalizes the current top-level ownership, resume, version and control projection', () => {
    const parsed = parseConversationProjection({
      contactId: CONTACT_ID,
      ownership: 'HUMAN',
      resumeStatus: 'SYNCHRONIZING',
      resumeId: 'resume-123',
      controlAvailability: {
        approvePaymentProof: true,
        recordHumanMessage: true,
        returnToUrba: true,
        internalEndpoint: '/secret/internal',
      },
      conversation: {
        mode: 'HUMAN',
        version: 7,
        internalOwnerId: 'secret-owner',
      },
      messages: [],
      turn: null,
    }, CONTACT_ID);

    expect(parsed).toMatchObject({
      ownership: 'HUMAN',
      resumeStatus: 'SYNCHRONIZING',
      resumeId: 'resume-123',
      conversationVersion: 7,
      controlAvailability: {
        approvePaymentProof: true,
        recordHumanMessage: true,
        returnToUrba: true,
      },
      conversation: { mode: 'HUMAN' },
    });
    expect(JSON.stringify(parsed)).not.toMatch(/secret-owner|internalEndpoint|secret\/internal/i);
  });

  it('preserves the version when a normalized projection is validated again', () => {
    const raw = {
      contactId: CONTACT_ID,
      conversation: { mode: 'HUMAN', version: 7 },
      messages: [],
      turn: null,
    };

    const normalized = parseConversationProjection(raw, CONTACT_ID);
    const revalidated = parseConversationProjection(normalized, CONTACT_ID);

    expect(revalidated.conversationVersion).toBe(7);
  });

  it('accepts all backend resume statuses while keeping the legacy statuses parseable', () => {
    for (const status of [
      'NONE',
      'PENDING',
      'SYNCHRONIZING',
      'DECIDING',
      'COMPLETED',
      'RETURNED_TO_HUMAN',
      'FAILED_SAFE',
      'RECONCILING',
      'FAILED_SAFE_TO_RETRY',
      'FAILED_TERMINAL',
    ]) {
      expect(parseConversationProjection({
        contactId: CONTACT_ID,
        ownership: 'URBA',
        resumeStatus: status,
        conversation: {},
        messages: [],
        turn: null,
      }, CONTACT_ID).resumeStatus).toBe(status);
    }
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

  it('parses the human-message and ownership-resume receipts without exposing technical fields', () => {
    expect(parseHumanMessageReceipt({
      eventId: 'human-event-1',
      status: 'RECORDED',
      duplicate: false,
      message: 'Mensagem humana registrada.',
      internalSessionId: 'secret-session',
    })).toEqual({
      eventId: 'human-event-1',
      status: 'RECORDED',
      duplicate: false,
      message: 'Mensagem humana registrada.',
    });

    expect(parseResumeReceipt({
      resumeId: 'resume-123',
      status: 'SYNCHRONIZING',
      ownership: 'HUMAN',
      message: null,
      duplicate: false,
      customerMessage: 'A conversa permanece com a arquiteta.',
      internalSessionId: 'secret-session',
    })).toEqual({
      resumeId: 'resume-123',
      status: 'SYNCHRONIZING',
      ownership: 'HUMAN',
      message: null,
      duplicate: false,
      customerMessage: 'A conversa permanece com a arquiteta.',
    });
  });
});
