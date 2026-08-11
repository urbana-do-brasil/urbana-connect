export const MAX_TEXT_LENGTH = 8_000;
export const CONTACT_ALIAS_PATTERN = /^manual-[a-f0-9-]{36}$/;
export const EVENT_ID_PATTERN = /^ui-[a-f0-9-]{36}$/;

export type MessageDirection = 'INBOUND' | 'OUTBOUND';
export type MessageSender = 'CONTACT' | 'URBA' | 'HUMAN' | 'SYSTEM';
export type ConversationMode = 'AI' | 'HUMAN';
export type TurnStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'DELAYED'
  | 'RECONCILING'
  | 'COMPLETED'
  | 'BLOCKED_BY_HUMAN'
  | 'FAILED_SAFE_TO_RETRY'
  | 'FAILED_TERMINAL';
export type TurnReceiptStatus = TurnStatus | 'DUPLICATE';

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
  turn: TurnSummary | null;
}

export interface TurnSummary {
  status: TurnStatus;
  correlationId: string;
  attempt: number;
  retryAllowed: boolean;
  failureClass: string | null;
  acceptedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface AgentOutput {
  message?: string;
  [key: string]: unknown;
}

export interface TurnReceipt {
  eventId: string;
  correlationId: string;
  status: TurnReceiptStatus;
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
    turn: value.turn === undefined || value.turn === null ? null : parseTurnSummary(value.turn),
  };
}

export function parseTurnSummary(value: unknown): TurnSummary {
  if (!isRecord(value)
      || !isTurnStatus(value.status)
      || !isNonEmptyString(value.correlationId)
      || typeof value.attempt !== 'number'
      || !Number.isInteger(value.attempt)
      || value.attempt < 1
      || typeof value.retryAllowed !== 'boolean'
      || (value.failureClass !== null && !isNonEmptyString(value.failureClass))
      || !isIsoDate(value.acceptedAt)
      || !isNullableIsoDate(value.startedAt)
      || !isNullableIsoDate(value.finishedAt)) {
    throw new Error('invalid turn summary');
  }

  return {
    status: value.status,
    correlationId: value.correlationId,
    attempt: value.attempt,
    retryAllowed: value.retryAllowed,
    failureClass: value.failureClass,
    acceptedAt: value.acceptedAt,
    startedAt: value.startedAt,
    finishedAt: value.finishedAt,
  };
}

export function parseTurnReceipt(value: unknown, expectedEventId: string): TurnReceipt {
  if (!isRecord(value)
      || value.eventId !== expectedEventId
      || !isNonEmptyString(value.correlationId)
      || !isTurnReceiptStatus(value.status)) {
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

export function isTurnStatus(value: unknown): value is TurnStatus {
  return value === 'QUEUED'
    || value === 'RUNNING'
    || value === 'DELAYED'
    || value === 'RECONCILING'
    || value === 'COMPLETED'
    || value === 'BLOCKED_BY_HUMAN'
    || value === 'FAILED_SAFE_TO_RETRY'
    || value === 'FAILED_TERMINAL';
}

function isTurnReceiptStatus(value: unknown): value is TurnReceiptStatus {
  return value === 'DUPLICATE' || isTurnStatus(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNullableIsoDate(value: unknown): value is string | null {
  return value === null || isIsoDate(value);
}
