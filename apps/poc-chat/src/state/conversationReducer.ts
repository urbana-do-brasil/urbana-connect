import {
  canonicalContactId,
  parseConversationProjection,
  type CanonicalMessage,
  type ConversationMode,
  type TurnStatus,
  type TurnSummary,
} from '../api/contracts';

export type LoadState = 'IDLE' | 'LOADING' | 'READY' | 'ERROR';
export type ProcessingState =
  | 'IDLE'
  | 'WAITING'
  | 'DELAYED'
  | 'RECONCILING'
  | 'FAILED_SAFE_TO_RETRY'
  | 'FAILED_TERMINAL'
  | 'HUMAN';
export type PendingSendState =
  | 'DRAFT'
  | 'ACCEPTING'
  | 'WAITING'
  | 'PERSISTED_WAITING'
  | 'DELAYED'
  | 'RECONCILING'
  | 'FAILED_SAFE_TO_RETRY'
  | 'FAILED_TERMINAL'
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
  turn: TurnSummary | null;
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
  | { type: 'POLLING_SLOW'; alias: string }
  | { type: 'MARK_READ'; alias: string };

export function createConversationUiState(): ConversationUiState {
  return {
    messages: [],
    optimisticMessages: [],
    turn: null,
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
        turn: null,
        optimisticMessages: current.optimisticMessages.map((item) => item.eventId === action.eventId
          ? { ...item, attempts: action.attempts, state: 'ACCEPTING', lastError: null }
          : item),
        processingState: current.mode === 'HUMAN' ? 'HUMAN' : 'WAITING',
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
        // A failed POST is not proof that the remote turn did not run. Keep the
        // optimistic event under observation until the projection authorizes a
        // terminal action; the retryable flag is intentionally ignored here.
        optimisticMessages: current.optimisticMessages.map((item) => item.eventId === action.eventId
          ? { ...item, state: activePendingState(item.state), lastError: action.error }
          : item),
        processingState: current.mode === 'HUMAN' ? 'HUMAN' : activeProcessingState(current.processingState),
        lastError: action.error,
      });
    case 'SYNC_FAILED':
      return withConversation(state, action.alias, {
        ...current,
        loadState: 'ERROR',
        // A GET failure only says that the browser lost synchronization. It
        // must never rewrite a canonical turn into a local retry state.
        lastError: action.error,
      });
    case 'POLLING_SLOW':
      return withConversation(state, action.alias, {
        ...current,
        processingState: isActiveProcessingState(current.processingState)
          && current.processingState !== 'RECONCILING'
          ? 'DELAYED'
          : current.processingState,
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
  if (!state || state.processingState === 'HUMAN') {
    return false;
  }
  if (state.optimisticMessages.some(isActivePending)) {
    // A new local send may be observed alongside the previous terminal turn
    // until the projection catches up with that send.
    return true;
  }
  if (state.turn !== null) {
    // A canonical terminal summary closes this tracker. The backend is
    // responsible for making a COMPLETED projection contain its output;
    // FAILED_* and human-blocked summaries must never be polled as retries.
    return isNonTerminalTurn(state.turn.status);
  }
  return state.optimisticMessages.some(isActivePending)
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
    const optimisticMessages = reconcilePendingMessages(
      current.optimisticMessages,
      messages,
      mode,
      projection.turn,
      alias,
    )
      .filter((pending) => !isCompletedPending(pending, messages));
    const processingState = deriveProcessingState(
      messages,
      optimisticMessages,
      mode,
      projection.turn,
      current.processingState,
    );
    return withConversation(state, alias, {
      ...current,
      messages,
      optimisticMessages,
      turn: projection.turn,
      loadState: 'READY',
      processingState,
      unread: activeAlias === alias ? false : current.unread || hasNewOutbound,
      lastSuccessfulSyncAt: new Date().toISOString(),
      lastError: null,
      mode,
    });
  } catch {
    return withConversation(state, alias, {
      ...current,
      loadState: 'ERROR',
      lastError: 'A projeção recebida pelo serviço local é inválida.',
    });
  }
}

function reconcilePendingMessages(
  pendingMessages: PendingSend[],
  messages: CanonicalMessage[],
  mode: ConversationMode,
  turn: TurnSummary | null,
  alias: string,
): PendingSend[] {
  const reconciled = pendingMessages.map((pending) => reconcilePending(
    pending,
    messages,
    mode,
    turn,
    pendingMessages.length === 1,
  ));
  if (turn?.status === 'FAILED_SAFE_TO_RETRY' && turn.retryAllowed) {
    const inbound = messages.find((message) => message.direction === 'INBOUND'
      && message.senderType === 'CONTACT'
      && message.correlationId === turn.correlationId);
    if (inbound && !reconciled.some((pending) => pending.eventId === inbound.eventId)) {
      reconciled.push({
        eventId: inbound.eventId,
        contactAlias: alias,
        text: inbound.text ?? '',
        occurredAt: inbound.createdAt,
        attempts: turn.attempt,
        state: 'FAILED_SAFE_TO_RETRY',
        lastError: turn.failureClass,
        correlationId: inbound.correlationId,
      });
    }
  }
  return reconciled;
}

function reconcilePending(
  pending: PendingSend,
  messages: CanonicalMessage[],
  mode: ConversationMode,
  turn: TurnSummary | null,
  isOnlyPending: boolean,
): PendingSend {
  const inbound = messages.find((message) => message.eventId === pending.eventId
    && message.direction === 'INBOUND');
  const correlationId = inbound?.correlationId
    ?? pending.correlationId
    ?? (isOnlyPending ? turn?.correlationId ?? null : null);
  return {
    ...pending,
    correlationId,
    state: stateForPending(mode, pending.state, turn, correlationId, inbound !== undefined),
    lastError: turn?.status === 'FAILED_SAFE_TO_RETRY' || turn?.status === 'FAILED_TERMINAL'
      ? turn.failureClass
      : pending.lastError,
  };
}

function stateForPending(
  mode: ConversationMode,
  previous: PendingSendState,
  turn: TurnSummary | null,
  correlationId: string | null,
  hasCanonicalInbound: boolean,
): PendingSendState {
  if (mode === 'HUMAN') {
    return 'HUMAN';
  }
  if (turn === null || (correlationId !== null && correlationId !== turn.correlationId)) {
    return hasCanonicalInbound && isActivePendingState(previous) ? 'PERSISTED_WAITING' : previous;
  }
  switch (turn.status) {
    case 'QUEUED':
    case 'RUNNING':
      return hasCanonicalInbound ? 'PERSISTED_WAITING' : activePendingState(previous);
    case 'DELAYED':
      return 'DELAYED';
    case 'RECONCILING':
      return 'RECONCILING';
    case 'FAILED_SAFE_TO_RETRY':
      return turn.retryAllowed ? 'FAILED_SAFE_TO_RETRY' : 'FAILED_TERMINAL';
    case 'FAILED_TERMINAL':
      return 'FAILED_TERMINAL';
    case 'BLOCKED_BY_HUMAN':
      return 'HUMAN';
    case 'COMPLETED':
      return previous === 'ACCEPTING' ? 'WAITING' : previous;
    default:
      return previous;
  }
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
  turn: TurnSummary | null,
  previous: ProcessingState,
): ProcessingState {
  if (mode === 'HUMAN') {
    return 'HUMAN';
  }
  if (turn !== null) {
    switch (turn.status) {
      case 'DELAYED':
        return 'DELAYED';
      case 'RECONCILING':
        return 'RECONCILING';
      case 'FAILED_SAFE_TO_RETRY':
        return turn.retryAllowed ? 'FAILED_SAFE_TO_RETRY' : 'FAILED_TERMINAL';
      case 'FAILED_TERMINAL':
        return 'FAILED_TERMINAL';
      case 'BLOCKED_BY_HUMAN':
        return 'HUMAN';
      case 'QUEUED':
      case 'RUNNING':
        return previous === 'DELAYED' ? 'DELAYED' : 'WAITING';
      case 'COMPLETED':
        break;
      default:
        break;
    }
  }
  if (optimisticMessages.some((pending) => pending.state === 'FAILED_SAFE_TO_RETRY')) {
    return 'FAILED_SAFE_TO_RETRY';
  }
  if (optimisticMessages.some((pending) => pending.state === 'FAILED_TERMINAL')) {
    return 'FAILED_TERMINAL';
  }
  return optimisticMessages.some(isActivePending)
    || hasUnansweredTurn(messages)
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

function isNonTerminalTurn(status: TurnStatus): boolean {
  return status === 'QUEUED'
    || status === 'RUNNING'
    || status === 'DELAYED'
    || status === 'RECONCILING';
}

function isActivePending(pending: PendingSend): boolean {
  return isActivePendingState(pending.state);
}

function isActivePendingState(state: PendingSendState): boolean {
  return state === 'DRAFT'
    || state === 'ACCEPTING'
    || state === 'WAITING'
    || state === 'PERSISTED_WAITING'
    || state === 'DELAYED'
    || state === 'RECONCILING';
}

function activePendingState(state: PendingSendState): PendingSendState {
  return isActivePendingState(state) ? state : 'WAITING';
}

function isActiveProcessingState(state: ProcessingState): boolean {
  return state === 'WAITING' || state === 'DELAYED' || state === 'RECONCILING';
}

function activeProcessingState(state: ProcessingState): ProcessingState {
  return isActiveProcessingState(state) ? state : 'WAITING';
}

function withConversation(
  state: ConversationState,
  alias: string,
  value: ConversationUiState,
): ConversationState {
  return { ...state, [alias]: value };
}
