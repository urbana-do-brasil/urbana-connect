import {
  ConversationHttpError,
  type ConversationApi,
} from '../api/conversationClient';
import {
  createEventId,
  type SyntheticTextPayload,
  type TurnReceipt,
} from '../api/contracts';
import {
  hasPendingWork,
  type ConversationAction,
  type ConversationState,
  type ConversationUiState,
  type PendingSend,
} from './conversationReducer';

export interface ConversationTrackerOptions {
  api: ConversationApi;
  dispatch: (action: ConversationAction) => void;
  getConversationState: (alias: string) => ConversationUiState | undefined;
  getActiveAlias: () => string | null;
  intervalMs?: number;
  maxIntervalMs?: number;
  slowAfterMs?: number;
  /** @deprecated Kept for callers compiled against the old API. It is not a terminal deadline. */
  maxWaitMs?: number;
}

export class ConversationTracker {
  private static readonly DEFAULT_INTERVAL_MS = 1_000;
  private static readonly DEFAULT_MAX_INTERVAL_MS = 10_000;
  private static readonly DEFAULT_SLOW_AFTER_MS = 30_000;
  private readonly api: ConversationApi;
  private readonly dispatch: ConversationTrackerOptions['dispatch'];
  private readonly getConversationState: ConversationTrackerOptions['getConversationState'];
  private readonly getActiveAlias: ConversationTrackerOptions['getActiveAlias'];
  private readonly intervalMs: number;
  private readonly maxIntervalMs: number;
  private readonly slowAfterMs: number;
  private readonly timers = new Map<string, ReturnType<typeof globalThis.setTimeout>>();
  private readonly slowTimers = new Map<string, ReturnType<typeof globalThis.setTimeout>>();
  private readonly inFlight = new Set<string>();
  private readonly pendingStarts = new Set<string>();
  private readonly startedAt = new Map<string, number>();
  private readonly backoffAttempts = new Map<string, number>();
  private disposed = false;

  constructor(options: ConversationTrackerOptions) {
    this.api = options.api;
    this.dispatch = options.dispatch;
    this.getConversationState = options.getConversationState;
    this.getActiveAlias = options.getActiveAlias;
    this.intervalMs = Math.max(1, options.intervalMs ?? ConversationTracker.DEFAULT_INTERVAL_MS);
    this.maxIntervalMs = Math.max(this.intervalMs, options.maxIntervalMs ?? ConversationTracker.DEFAULT_MAX_INTERVAL_MS);
    this.slowAfterMs = Math.max(0, options.slowAfterMs ?? ConversationTracker.DEFAULT_SLOW_AFTER_MS);
  }

  async send(
    contactAlias: string,
    text: string,
    occurredAt: string = new Date().toISOString(),
    eventId: string = createEventId(),
  ): Promise<TurnReceipt | null> {
    const payload: SyntheticTextPayload = { eventId, type: 'TEXT', text, occurredAt };
    const pending: PendingSend = {
      eventId,
      contactAlias,
      text,
      occurredAt,
      attempts: 1,
      state: 'ACCEPTING',
      lastError: null,
      correlationId: null,
    };
    this.dispatch({ type: 'SEND_STARTED', alias: contactAlias, pending });
    return this.submit(payload, contactAlias);
  }

  async retry(contactAlias: string, eventId: string): Promise<TurnReceipt | null> {
    const state = this.getConversationState(contactAlias);
    const pending = state?.optimisticMessages.find((candidate) => candidate.eventId === eventId);
    const turn = state?.turn;
    if (!pending
        || pending.state !== 'FAILED_SAFE_TO_RETRY'
        || turn?.status !== 'FAILED_SAFE_TO_RETRY'
        || turn.retryAllowed !== true) {
      return null;
    }
    this.dispatch({
      type: 'RETRY_STARTED',
      alias: contactAlias,
      eventId,
      attempts: pending.attempts + 1,
    });
    return this.submit({
      eventId: pending.eventId,
      type: 'TEXT',
      text: pending.text,
      occurredAt: pending.occurredAt,
    }, contactAlias);
  }

  async sync(contactAlias: string): Promise<void> {
    if (this.disposed || this.inFlight.has(contactAlias)) {
      return;
    }
    this.beginTracking(contactAlias);
    this.dispatch({ type: 'LOAD_STARTED', alias: contactAlias });
    await this.pollOnce(contactAlias, false);
  }

  async startPolling(contactAlias: string, force = false): Promise<void> {
    if (this.disposed || this.timers.has(contactAlias)) {
      return;
    }
    if (this.inFlight.has(contactAlias)) {
      if (force) {
        this.pendingStarts.add(contactAlias);
      }
      return;
    }
    if (!force && !hasPendingWork(this.getConversationState(contactAlias))) {
      return;
    }
    this.beginTracking(contactAlias);
    await this.pollOnce(contactAlias, true);
  }

  stop(contactAlias: string): void {
    const timer = this.timers.get(contactAlias);
    if (timer !== undefined) {
      globalThis.clearTimeout(timer);
      this.timers.delete(contactAlias);
    }
    const slowTimer = this.slowTimers.get(contactAlias);
    if (slowTimer !== undefined) {
      globalThis.clearTimeout(slowTimer);
      this.slowTimers.delete(contactAlias);
    }
    this.startedAt.delete(contactAlias);
    this.backoffAttempts.delete(contactAlias);
    this.pendingStarts.delete(contactAlias);
  }

  dispose(): void {
    this.disposed = true;
    this.pendingStarts.clear();
    const aliases = new Set([...this.timers.keys(), ...this.slowTimers.keys(), ...this.startedAt.keys()]);
    for (const alias of aliases) {
      this.stop(alias);
    }
  }

  private async submit(
    payload: SyntheticTextPayload,
    contactAlias: string,
  ): Promise<TurnReceipt | null> {
    try {
      const receipt = await this.api.sendTextMessage(contactAlias, payload);
      this.dispatch({
        type: 'RECEIPT_RECEIVED',
        alias: contactAlias,
        eventId: payload.eventId,
        correlationId: receipt.correlationId,
        status: receipt.status,
      });
      // The receipt is only an acceptance. The canonical projection remains
      // the sole source for completion and output.
      await this.startPolling(contactAlias, true);
      return receipt;
    } catch (error) {
      const retryable = error instanceof ConversationHttpError ? error.retryable : false;
      this.dispatch({
        type: 'SEND_FAILED',
        alias: contactAlias,
        eventId: payload.eventId,
        error: sanitizeError(error),
        // Reducer intentionally ignores this local transport hint. Only the
        // backend turn summary may authorize a retry.
        retryable,
      });
      // A transport timeout is ambiguous. Observe the same event through GET;
      // never generate a new event or issue an automatic second POST.
      await this.startPolling(contactAlias, true);
      return null;
    }
  }

  private async pollOnce(contactAlias: string, continuePolling: boolean): Promise<void> {
    if (this.disposed || this.inFlight.has(contactAlias)) {
      return;
    }
    this.inFlight.add(contactAlias);
    let succeeded = false;
    try {
      const projection = await this.api.getConversationProjection(contactAlias);
      succeeded = true;
      this.dispatch({
        type: 'PROJECTION_RECEIVED',
        alias: contactAlias,
        projection,
        activeAlias: this.getActiveAlias(),
      });
      this.ensureSlowIndicator(contactAlias);
    } catch (error) {
      this.dispatch({ type: 'SYNC_FAILED', alias: contactAlias, error: sanitizeError(error) });
    } finally {
      this.inFlight.delete(contactAlias);
    }

    if (this.pendingStarts.delete(contactAlias)) {
      await this.startPolling(contactAlias, true);
      return;
    }

    const pending = hasPendingWork(this.getConversationState(contactAlias));
    if (!succeeded) {
      // A GET error is recoverable synchronization loss. Keep trying even when
      // reload left no optimistic copy in memory; a successful projection can
      // then decide whether there is work to continue.
      this.scheduleNext(contactAlias, true);
      return;
    }
    if (!continuePolling && pending) {
      await this.startPolling(contactAlias);
      return;
    }
    if (continuePolling && pending) {
      this.scheduleNext(contactAlias, false);
      return;
    }
    this.stop(contactAlias);
  }

  private scheduleNext(contactAlias: string, allowWithoutPending: boolean): void {
    if (this.disposed || (!allowWithoutPending && !hasPendingWork(this.getConversationState(contactAlias)))) {
      this.stop(contactAlias);
      return;
    }
    const attempt = this.backoffAttempts.get(contactAlias) ?? 0;
    const delay = Math.min(this.maxIntervalMs, this.intervalMs * (2 ** Math.min(attempt, 10)));
    this.backoffAttempts.set(contactAlias, attempt + 1);
    const timer = globalThis.setTimeout(() => {
      this.timers.delete(contactAlias);
      void this.pollOnce(contactAlias, true);
    }, delay);
    this.timers.set(contactAlias, timer);
  }

  private beginTracking(contactAlias: string): void {
    if (this.startedAt.has(contactAlias)) {
      return;
    }
    this.startedAt.set(contactAlias, Date.now());
    this.backoffAttempts.set(contactAlias, 0);
    this.scheduleSlowIndicator(contactAlias, this.slowAfterMs);
  }

  private scheduleSlowIndicator(contactAlias: string, delay: number): void {
    if (this.disposed || this.slowTimers.has(contactAlias)) {
      return;
    }
    const timer = globalThis.setTimeout(() => {
      this.slowTimers.delete(contactAlias);
      if (hasPendingWork(this.getConversationState(contactAlias))) {
        this.dispatch({ type: 'POLLING_SLOW', alias: contactAlias });
      }
    }, delay);
    this.slowTimers.set(contactAlias, timer);
  }

  private ensureSlowIndicator(contactAlias: string): void {
    if (!hasPendingWork(this.getConversationState(contactAlias)) || this.slowTimers.has(contactAlias)) {
      return;
    }
    const startedAt = this.startedAt.get(contactAlias) ?? Date.now();
    const elapsed = Date.now() - startedAt;
    if (elapsed >= this.slowAfterMs) {
      this.dispatch({ type: 'POLLING_SLOW', alias: contactAlias });
      return;
    }
    this.scheduleSlowIndicator(contactAlias, this.slowAfterMs - elapsed);
  }
}

export function conversationHasPendingWork(
  state: ConversationState,
  alias: string,
): boolean {
  return hasPendingWork(state[alias]);
}

function sanitizeError(error: unknown): string {
  if (error instanceof ConversationHttpError) {
    return error.message;
  }
  return 'Não foi possível acompanhar a mensagem no serviço local.';
}
