import type {
  CanonicalMessage,
  ConversationProjection,
  TurnReceipt,
  TurnSummary,
} from '../api/contracts';

export const FIXTURE_ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
export const FIXTURE_EVENT_ID = 'ui-22222222-2222-4222-8222-222222222222';
export const FIXTURE_TIMESTAMP = '2026-08-06T12:00:00.000Z';

export function turnSummary(overrides: Partial<TurnSummary> = {}): TurnSummary {
  return {
    status: 'RUNNING',
    correlationId: 'correlation-1',
    attempt: 1,
    retryAllowed: false,
    failureClass: null,
    acceptedAt: FIXTURE_TIMESTAMP,
    startedAt: FIXTURE_TIMESTAMP,
    finishedAt: null,
    ...overrides,
  };
}

export function textMessage(
  overrides: Partial<CanonicalMessage> = {},
): CanonicalMessage {
  return {
    id: 'message-1',
    eventId: FIXTURE_EVENT_ID,
    correlationId: 'correlation-1',
    contactId: `poc:${FIXTURE_ALIAS}`,
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: 'Oi',
    createdAt: FIXTURE_TIMESTAMP,
    ...overrides,
  };
}

export function projection(
  alias = FIXTURE_ALIAS,
  messages: CanonicalMessage[] = [],
  conversation: Record<string, unknown> = {},
  turn: TurnSummary | null = null,
): ConversationProjection {
  return {
    contactId: `poc:${alias}`,
    conversation,
    messages,
    turn,
  };
}

export function receipt(
  overrides: Partial<TurnReceipt> = {},
): TurnReceipt {
  return {
    eventId: FIXTURE_EVENT_ID,
    correlationId: 'correlation-1',
    status: 'QUEUED',
    output: null,
    error: null,
    ...overrides,
  };
}
