import {
  canonicalContactId,
  parseConversationProjection,
  type CanonicalMessage,
  type ConversationMode,
} from '../api/contracts';

export type LoadState = 'IDLE' | 'LOADING' | 'READY' | 'ERROR';
export type ProcessingState = 'IDLE' | 'WAITING' | 'FAILED_RETRYABLE' | 'HUMAN';
export type PendingSendState =
  | 'DRAFT'
  | 'ACCEPTING'
  | 'WAITING'
  | 'PERSISTED_WAITING'
  | 'FAILED_RETRYABLE'
  | 'HUMAN'
  | 'COMPLETED';

export interface PendingSend {
  eventId: string;
  contactAlias: string;
  text: string;
  occurredAt: string;
  attempts: number;
  state: PendingSendState;
  lastError: string | null;
  correlationId: string | null;
}

export interface ConversationUiState {
  messages: CanonicalMessage[];
  optimisticMessages: PendingSend[];
  loadState: LoadState;
  processingState: ProcessingState;
  unread: boolean;
  lastSuccessfulSyncAt: string | null;
  lastError: string | null;
  mode: ConversationMode | null;
}

export type ConversationState = Record<string, ConversationUiState>;

export type ConversationAction =
  | { type: 'LOAD_STARTED'; alias: string }
  | { type: 'PROJECTION_RECEIVED'; alias: string; projection: unknown; activeAlias?: string | null }
  | { type: 'LOAD_FAILED'; alias: string; error: string }
  | { type: 'SEND_STARTED'; alias: string; pending: PendingSend }
  | { type: 'RETRY_STARTED'; alias: string; eventId: string; attempts: number }
  | { type: 'RECEIPT_RECEIVED'; alias: string; eventId: string; correlationId: string; status: string }
  | { type: 'SEND_FAILED'; alias: string; eventId: string; error: string; retryable: boolean }
  | { type: 'SYNC_FAILED'; alias: string; error: string }
  | { type: 'MARK_READ'; alias: string };

export function createConversationUiState(): ConversationUiState {
  return {
    messages: [],
    optimisticMessages: [],
    loadState: 'IDLE',
    processingState: 'IDLE',
    unread: false,
    lastSuccessfulSyncAt: null,
    lastError: null,
    mode: null,
  };
}

export function createConversationState(): ConversationState {
  return {};
}

export function conversationReducer(
  state: ConversationState,
  action: ConversationAction,
): ConversationState {
  const current = state[action.alias] ?? createConversationUiState();

  switch (action.type) {
    case 'LOAD_STARTED':
      return withConversation(state, action.alias, {
        ...current,
        loadState: 'LOADING',
        lastError: null,
      });
    case 'PROJECTION_RECEIVED':
      return reconcileProjection(state, action.alias, current, action.projection, action.activeAlias);
    case 'LOAD_FAILED':
      return withConversation(state, action.alias, {
        ...current,
        loadState: 'ERROR',
        lastError: action.error,
      });
    case 'SEND_STARTED':
      return withConversation(state, action.alias, {
        ...current,
        optimisticMessages: current.optimisticMessages.some((item) => item.eventId === action.pending.eventId)
          ? current.optimisticMessages
          : [...current.optimisticMessages, action.pending],
        processingState: current.mode === 'HUMAN' ? 'HUMAN' : 'WAITING',
        lastError: null,
      });
    case 'RETRY_STARTED':
      return withConversation(state, action.alias, {
        ...current,
        optimisticMessages: current.optimisticMessages.map((item) => item.eventId === action.eventId
          ? { ...item, attempts: action.attempts, state: 'ACCEPTING', lastError: null }
          : item),
        processingState: 'WAITING',
        lastError: null,
      });
    case 'RECEIPT_RECEIVED':
      return withConversation(state, action.alias, {
        ...current,
        optimisticMessages: current.optimisticMessages.map((item) => item.eventId === action.eventId
          ? {
            ...item,
            correlationId: action.correlationId,
            state: action.status === 'BLOCKED_BY_HUMAN' ? 'HUMAN' : 'WAITING',
          }
          : item),
        processingState: action.status === 'BLOCKED_BY_HUMAN' ? 'HUMAN' : 'WAITING',
        lastError: null,
      });
    case 'SEND_FAILED':
      return withConversation(state, action.alias, {
        ...current,
        optimisticMessages: current.optimisticMessages.map((item) => item.eventId === action.eventId
          ? {
            ...item,
            state: action.retryable ? 'FAILED_RETRYABLE' : 'FAILED_RETRYABLE',
            lastError: action.error,
          }
          : item),
        processingState: 'FAILED_RETRYABLE',
        lastError: action.error,
      });
    case 'SYNC_FAILED':
      return withConversation(state, action.alias, {
        ...current,
        loadState: current.loadState === 'IDLE' ? 'ERROR' : current.loadState,
        optimisticMessages: current.optimisticMessages.map((item) => ({
          ...item,
          state: 'FAILED_RETRYABLE',
          lastError: action.error,
        })),
        processingState: 'FAILED_RETRYABLE',
        lastError: action.error,
      });
    case 'MARK_READ':
      return withConversation(state, action.alias, { ...current, unread: false });
    default:
      return state;
  }
}

export function getVisibleMessages(state: ConversationUiState): CanonicalMessage[] {
  const canonicalEventIds = new Set(state.messages.map((message) => message.eventId));
  const optimistic = state.optimisticMessages
    .filter((pending) => !canonicalEventIds.has(pending.eventId))
    .map(optimisticMessage);
  return [...state.messages, ...optimistic]
    .map((message, index) => ({ message, index }))
    .sort((left, right) => {
      const byTime = Date.parse(left.message.createdAt) - Date.parse(right.message.createdAt);
      return byTime === 0 ? left.index - right.index : byTime;
    })
    .map(({ message }) => message);
}

export function hasPendingWork(state: ConversationUiState | undefined): boolean {
  if (!state || state.processingState !== 'WAITING') {
    return false;
  }
  return state.optimisticMessages.some((pending) => pending.state !== 'COMPLETED' && pending.state !== 'HUMAN')
    || hasUnansweredTurn(state.messages);
}

export function hasUnansweredTurn(messages: CanonicalMessage[]): boolean {
  const inboundCorrelations = new Set(
    messages
      .filter((message) => message.direction === 'INBOUND' && message.senderType === 'CONTACT')
      .map((message) => message.correlationId),
  );
  const answeredCorrelations = new Set(
    messages
      .filter((message) => message.direction === 'OUTBOUND'
        && (message.senderType === 'URBA' || message.senderType === 'HUMAN'))
      .map((message) => message.correlationId),
  );
  return [...inboundCorrelations].some((correlationId) => !answeredCorrelations.has(correlationId));
}

function reconcileProjection(
  state: ConversationState,
  alias: string,
  current: ConversationUiState,
  rawProjection: unknown,
  activeAlias: string | null | undefined,
): ConversationState {
  try {
    const projection = parseConversationProjection(rawProjection, canonicalContactId(alias));
    const messages = deduplicateAndSort(projection.messages, canonicalContactId(alias));
    const outboundIds = new Set(messages
      .filter((message) => message.direction === 'OUTBOUND')
      .map((message) => message.id));
    const oldOutboundIds = new Set(current.messages
      .filter((message) => message.direction === 'OUTBOUND')
      .map((message) => message.id));
    const hasNewOutbound = [...outboundIds].some((id) => !oldOutboundIds.has(id));
    const mode = projection.conversation.mode === 'HUMAN' ? 'HUMAN' : 'AI';
    const optimisticMessages = current.optimisticMessages
      .map((pending) => reconcilePending(pending, messages, mode))
      .filter((pending) => !isCompletedPending(pending, messages));
    const processingState = deriveProcessingState(messages, optimisticMessages, mode);
    return withConversation(state, alias, {
      ...current,
      messages,
      optimisticMessages,
      loadState: 'READY',
      processingState,
      unread: activeAlias === alias ? false : current.unread || hasNewOutbound,
      lastSuccessfulSyncAt: new Date().toISOString(),
      lastError: null,
      mode: projection.conversation.mode === 'HUMAN' ? 'HUMAN' : 'AI',
    });
  } catch {
    return withConversation(state, alias, {
      ...current,
      loadState: 'ERROR',
      lastError: 'A projeção recebida pelo serviço local é inválida.',
    });
  }
}

function reconcilePending(
  pending: PendingSend,
  messages: CanonicalMessage[],
  mode: ConversationMode,
): PendingSend {
  const inbound = messages.find((message) => message.eventId === pending.eventId
    && message.direction === 'INBOUND');
  return {
    ...pending,
    correlationId: inbound?.correlationId ?? pending.correlationId,
    state: mode === 'HUMAN'
      ? 'HUMAN'
      : inbound ? 'PERSISTED_WAITING' : pending.state,
  };
}

function isCompletedPending(pending: PendingSend, messages: CanonicalMessage[]): boolean {
  if (pending.state === 'HUMAN') {
    return false;
  }
  const inbound = messages.find((message) => message.eventId === pending.eventId
    && message.direction === 'INBOUND');
  if (!inbound) {
    return false;
  }
  return messages.some((message) => message.direction === 'OUTBOUND'
    && (message.senderType === 'URBA' || message.senderType === 'HUMAN')
    && message.correlationId === inbound.correlationId);
}

function deriveProcessingState(
  messages: CanonicalMessage[],
  optimisticMessages: PendingSend[],
  mode: ConversationMode,
): ProcessingState {
  if (mode === 'HUMAN') {
    return 'HUMAN';
  }
  if (optimisticMessages.some((pending) => pending.state === 'FAILED_RETRYABLE')) {
    return 'FAILED_RETRYABLE';
  }
  return optimisticMessages.some((pending) => pending.state !== 'COMPLETED') || hasUnansweredTurn(messages)
    ? 'WAITING'
    : 'IDLE';
}

function deduplicateAndSort(messages: CanonicalMessage[], contactId: string): CanonicalMessage[] {
  const seenIds = new Set<string>();
  const seenFallbacks = new Set<string>();
  return messages
    .filter((message) => message.contactId === contactId
      && message.type === 'TEXT'
      && typeof message.text === 'string'
      && (message.direction === 'INBOUND' && message.senderType === 'CONTACT'
        || message.direction === 'OUTBOUND' && (message.senderType === 'URBA' || message.senderType === 'HUMAN')))
    .filter((message) => {
      const fallback = `${message.eventId}|${message.direction}|${message.correlationId}`;
      if (seenIds.has(message.id) || seenFallbacks.has(fallback)) {
        return false;
      }
      seenIds.add(message.id);
      seenFallbacks.add(fallback);
      return true;
    })
    .sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt));
}

function optimisticMessage(pending: PendingSend): CanonicalMessage {
  return {
    id: `optimistic-${pending.eventId}`,
    eventId: pending.eventId,
    correlationId: pending.correlationId ?? `pending-${pending.eventId}`,
    contactId: canonicalContactId(pending.contactAlias),
    direction: 'INBOUND',
    senderType: 'CONTACT',
    type: 'TEXT',
    text: pending.text,
    createdAt: pending.occurredAt,
  };
}

function withConversation(
  state: ConversationState,
  alias: string,
  value: ConversationUiState,
): ConversationState {
  return { ...state, [alias]: value };
}
