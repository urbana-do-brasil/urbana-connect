export const MAX_TEXT_LENGTH = 8_000;
export const CONTACT_ALIAS_PATTERN = /^manual-[a-f0-9-]{36}$/;
export const EVENT_ID_PATTERN = /^ui-[a-f0-9-]{36}$/;

export type MessageDirection = 'INBOUND' | 'OUTBOUND';
export type MessageSender = 'CONTACT' | 'URBA' | 'HUMAN' | 'SYSTEM';
export type ConversationMode = 'AI' | 'HUMAN';
export type ConversationOwnership = 'URBA' | 'HUMAN';
export type ResumeStatus =
  | 'NONE'
  | 'PENDING'
  | 'SYNCHRONIZING'
  | 'DECIDING'
  | 'COMPLETED'
  | 'RETURNED_TO_HUMAN'
  | 'FAILED_SAFE'
  // Legacy projection values remain accepted during the POC transition.
  | 'IDLE'
  | 'WAITING'
  | 'RECONCILING'
  | 'FAILED_SAFE_TO_RETRY'
  | 'FAILED_TERMINAL';
export type PocControlAction =
  | 'APPROVE_PAYMENT_PROOF'
  | 'RECORD_HUMAN_MESSAGE'
  | 'RETURN_TO_URBA';
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
  ownership?: ConversationOwnership;
  resume?: ResumeSummary | null;
  pocControls?: PocControlAvailability | null;
  resumeStatus?: ResumeStatus;
  resumeId?: string | null;
  controlAvailability?: PocControlAvailability | null;
}

export interface ResumeSummary {
  status: ResumeStatus;
  retryAllowed: boolean;
  failureClass: string | null;
}

export interface PocControlAvailability {
  approvePaymentProof: boolean;
  recordHumanMessage: boolean;
  returnToUrba: boolean;
}

export interface ConversationProjection {
  contactId: string;
  conversation: ConversationSummary;
  messages: CanonicalMessage[];
  turn: TurnSummary | null;
  ownership?: ConversationOwnership;
  resumeStatus?: ResumeStatus;
  resumeId?: string | null;
  controlAvailability?: PocControlAvailability;
  conversationVersion?: number;
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

export interface HumanMessagePayload {
  text: string;
  occurredAt: string;
}

export interface HumanMessageReceipt {
  eventId: string;
  status: string;
  duplicate: boolean;
  message: string | null;
}

export interface ResumeReceipt {
  resumeId: string | null;
  status: ResumeStatus;
  ownership: ConversationOwnership;
  message: string | null;
  duplicate: boolean;
  customerMessage: string | null;
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

  const conversation = parseConversationSummary(value.conversation);
  const projection: ConversationProjection = {
    contactId: value.contactId,
    conversation,
    messages: value.messages.filter(isCanonicalMessage),
    turn: value.turn === undefined || value.turn === null ? null : parseTurnSummary(value.turn),
  };

  const ownership = parseOwnership(value.ownership);
  if (ownership !== undefined) {
    projection.ownership = ownership;
    conversation.ownership = ownership;
  }

  const resumeStatus = parseResumeStatus(value.resumeStatus);
  if (resumeStatus !== undefined) {
    projection.resumeStatus = resumeStatus;
    conversation.resumeStatus = resumeStatus;
  }

  if (hasOwn(value, 'resumeId')) {
    const resumeId = parseNullableString(value.resumeId);
    projection.resumeId = resumeId;
    conversation.resumeId = resumeId;
  }

  const controlAvailability = parseControlAvailability(value.controlAvailability);
  if (controlAvailability !== null) {
    projection.controlAvailability = controlAvailability;
    conversation.controlAvailability = controlAvailability;
  }

  // The HTTP client returns this normalized projection to the reducer, which
  // validates it once more before committing it to UI state. Preserve the
  // normalized field as well as the raw backend locations so optimistic and
  // canonical paths expose the same concurrency version.
  const conversationVersion = parseVersion(value.version)
    ?? parseVersion(value.conversationVersion)
    ?? parseVersion(value.conversation.version);
  if (conversationVersion !== undefined) {
    projection.conversationVersion = conversationVersion;
  }

  return projection;
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

export function parseTurnReceipt(value: unknown, expectedEventId?: string): TurnReceipt {
  if (!isRecord(value)
      || !isNonEmptyString(value.eventId)
      || (expectedEventId !== undefined && value.eventId !== expectedEventId)
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

export function parseHumanMessageReceipt(value: unknown): HumanMessageReceipt {
  if (!isRecord(value)
      || !isNonEmptyString(value.eventId)
      || !isNonEmptyString(value.status)
      || typeof value.duplicate !== 'boolean'
      || !isNullableString(value.message)) {
    throw new Error('invalid human message receipt');
  }

  return {
    eventId: value.eventId,
    status: value.status,
    duplicate: value.duplicate,
    message: value.message,
  };
}

export function parseResumeReceipt(value: unknown): ResumeReceipt {
  if (!isRecord(value)
      || !isNullableString(value.resumeId)
      || !isResumeStatus(value.status)
      || !isOwnership(value.ownership)
      || !isNullableString(value.message)
      || typeof value.duplicate !== 'boolean'
      || !isNullableString(value.customerMessage)) {
    throw new Error('invalid resume receipt');
  }

  return {
    resumeId: value.resumeId,
    status: value.status,
    ownership: value.ownership,
    message: value.message,
    duplicate: value.duplicate,
    customerMessage: value.customerMessage,
  };
}

export function createEventId(uuidFactory: () => string = () => crypto.randomUUID()): string {
  return `ui-${uuidFactory()}`;
}

export function createIdempotencyKey(uuidFactory: () => string = () => crypto.randomUUID()): string {
  return `poc-${uuidFactory()}`;
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

function parseConversationSummary(value: Record<string, unknown>): ConversationSummary {
  const summary: ConversationSummary = {};
  if (value.mode === 'AI' || value.mode === 'HUMAN') {
    summary.mode = value.mode;
  }
  if (value.ownership === 'URBA' || value.ownership === 'HUMAN') {
    summary.ownership = value.ownership;
  }
  if (value.resume === null) {
    summary.resume = null;
  } else if (isRecord(value.resume)) {
    const resume = parseResumeSummary(value.resume);
    if (resume !== null) {
      summary.resume = resume;
    }
  }
  if (isRecord(value.pocControls)) {
    summary.pocControls = {
      approvePaymentProof: value.pocControls.approvePaymentProof === true,
      recordHumanMessage: value.pocControls.recordHumanMessage === true
        || value.pocControls.recordDecision === true,
      returnToUrba: value.pocControls.returnToUrba === true,
    };
  }
  const resumeStatus = parseResumeStatus(value.resumeStatus);
  if (resumeStatus !== undefined) {
    summary.resumeStatus = resumeStatus;
  }
  if (hasOwn(value, 'resumeId')) {
    summary.resumeId = parseNullableString(value.resumeId);
  }
  const controlAvailability = parseControlAvailability(value.controlAvailability);
  if (controlAvailability !== null) {
    summary.controlAvailability = controlAvailability;
  }
  return summary;
}

function parseResumeSummary(value: Record<string, unknown>): ResumeSummary | null {
  if (!isResumeStatus(value.status)
      || typeof value.retryAllowed !== 'boolean'
      || (value.failureClass !== null && !isNonEmptyString(value.failureClass))) {
    return null;
  }
  return {
    status: value.status,
    retryAllowed: value.retryAllowed,
    failureClass: value.failureClass,
  };
}

function isResumeStatus(value: unknown): value is ResumeStatus {
  return value === 'NONE'
    || value === 'PENDING'
    || value === 'SYNCHRONIZING'
    || value === 'DECIDING'
    || value === 'COMPLETED'
    || value === 'RETURNED_TO_HUMAN'
    || value === 'FAILED_SAFE'
    || value === 'IDLE'
    || value === 'WAITING'
    || value === 'RECONCILING'
    || value === 'FAILED_SAFE_TO_RETRY'
    || value === 'FAILED_TERMINAL';
}

function parseResumeStatus(value: unknown): ResumeStatus | undefined {
  return isResumeStatus(value) ? value : undefined;
}

function parseOwnership(value: unknown): ConversationOwnership | undefined {
  return isOwnership(value) ? value : undefined;
}

function isOwnership(value: unknown): value is ConversationOwnership {
  return value === 'URBA' || value === 'HUMAN';
}

function parseControlAvailability(value: unknown): PocControlAvailability | null {
  if (!isRecord(value)) {
    return null;
  }
  return {
    approvePaymentProof: value.approvePaymentProof === true,
    recordHumanMessage: value.recordHumanMessage === true || value.recordDecision === true,
    returnToUrba: value.returnToUrba === true,
  };
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function parseNullableString(value: unknown): string | null {
  return isNonEmptyString(value) ? value : null;
}

function parseVersion(value: unknown): number | undefined {
  return typeof value === 'number'
    && Number.isSafeInteger(value)
    && value >= 0
    ? value
    : undefined;
}

function hasOwn(value: Record<string, unknown>, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(value, key);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNullableIsoDate(value: unknown): value is string | null {
  return value === null || isIsoDate(value);
}
