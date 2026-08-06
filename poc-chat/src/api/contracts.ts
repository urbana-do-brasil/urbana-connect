export const MAX_TEXT_LENGTH = 8_000;
export const CONTACT_ALIAS_PATTERN = /^manual-[a-f0-9-]{36}$/;
export const EVENT_ID_PATTERN = /^ui-[a-f0-9-]{36}$/;

export type MessageDirection = 'INBOUND' | 'OUTBOUND';
export type MessageSender = 'CONTACT' | 'URBA' | 'HUMAN' | 'SYSTEM';
export type ConversationMode = 'AI' | 'HUMAN';
export type TurnStatus =
  | 'QUEUED'
  | 'COMPLETED'
  | 'DUPLICATE'
  | 'BLOCKED_BY_HUMAN'
  | 'FAILED'
  | 'FAILED_RETRYABLE';

export interface CanonicalMessage {
  id: string;
  eventId: string;
  correlationId: string;
  contactId: string;
  direction: MessageDirection;
  senderType: MessageSender;
  type: string;
  text: string | null;
  createdAt: string;
}

export interface ConversationSummary {
  mode?: ConversationMode;
  [key: string]: unknown;
}

export interface ConversationProjection {
  contactId: string;
  conversation: ConversationSummary;
  messages: CanonicalMessage[];
}

export interface AgentOutput {
  message?: string;
  [key: string]: unknown;
}

export interface TurnReceipt {
  eventId: string;
  correlationId: string;
  status: TurnStatus;
  output?: AgentOutput | null;
  error?: string | null;
}

export interface SyntheticTextPayload {
  eventId: string;
  type: 'TEXT';
  text: string;
  occurredAt: string;
}

export function canonicalContactId(contactAlias: string): string {
  return `poc:${contactAlias}`;
}

export function isContactAlias(value: unknown): value is string {
  return typeof value === 'string' && CONTACT_ALIAS_PATTERN.test(value);
}

export function isEventId(value: unknown): value is string {
  return typeof value === 'string' && EVENT_ID_PATTERN.test(value);
}

export function isIsoDate(value: unknown): value is string {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}

export function isCanonicalMessage(value: unknown): value is CanonicalMessage {
  if (!isRecord(value)) {
    return false;
  }
  return isNonEmptyString(value.id)
    && isNonEmptyString(value.eventId)
    && isNonEmptyString(value.correlationId)
    && isNonEmptyString(value.contactId)
    && (value.direction === 'INBOUND' || value.direction === 'OUTBOUND')
    && (value.senderType === 'CONTACT'
      || value.senderType === 'URBA'
      || value.senderType === 'HUMAN'
      || value.senderType === 'SYSTEM')
    && typeof value.type === 'string'
    && (typeof value.text === 'string' || value.text === null)
    && isIsoDate(value.createdAt);
}

export function parseConversationProjection(
  value: unknown,
  expectedContactId: string,
): ConversationProjection {
  if (!isRecord(value)
      || value.contactId !== expectedContactId
      || !isRecord(value.conversation)
      || !Array.isArray(value.messages)) {
    throw new Error('invalid conversation projection');
  }

  return {
    contactId: value.contactId,
    conversation: value.conversation as ConversationSummary,
    messages: value.messages.filter(isCanonicalMessage),
  };
}

export function parseTurnReceipt(value: unknown, expectedEventId: string): TurnReceipt {
  if (!isRecord(value)
      || value.eventId !== expectedEventId
      || !isNonEmptyString(value.correlationId)
      || !isTurnStatus(value.status)) {
    throw new Error('invalid turn receipt');
  }

  return {
    eventId: value.eventId,
    correlationId: value.correlationId,
    status: value.status,
    output: isRecord(value.output) ? value.output as AgentOutput : null,
    error: typeof value.error === 'string' ? value.error : null,
  };
}

export function createEventId(uuidFactory: () => string = () => crypto.randomUUID()): string {
  return `ui-${uuidFactory()}`;
}

export function createContactAlias(uuidFactory: () => string = () => crypto.randomUUID()): string {
  return `manual-${uuidFactory()}`;
}

function isTurnStatus(value: unknown): value is TurnStatus {
  return value === 'QUEUED'
    || value === 'COMPLETED'
    || value === 'DUPLICATE'
    || value === 'BLOCKED_BY_HUMAN'
    || value === 'FAILED'
    || value === 'FAILED_RETRYABLE';
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
