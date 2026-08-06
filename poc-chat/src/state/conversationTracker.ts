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
  maxWaitMs?: number;
}

export class ConversationTracker {
  private static readonly DEFAULT_MAX_WAIT_MS = 120_000;
  private readonly api: ConversationApi;
  private readonly dispatch: ConversationTrackerOptions['dispatch'];
  private readonly getConversationState: ConversationTrackerOptions['getConversationState'];
  private readonly getActiveAlias: ConversationTrackerOptions['getActiveAlias'];
  private readonly intervalMs: number;
  private readonly maxWaitMs: number;
  private readonly timers = new Map<string, ReturnType<typeof globalThis.setTimeout>>();
  private readonly inFlight = new Set<string>();
  private readonly pendingStarts = new Set<string>();
  private readonly startedAt = new Map<string, number>();
  private disposed = false;

  constructor(options: ConversationTrackerOptions) {
    this.api = options.api;
    this.dispatch = options.dispatch;
    this.getConversationState = options.getConversationState;
    this.getActiveAlias = options.getActiveAlias;
    this.intervalMs = options.intervalMs ?? 1_000;
    // A real Hermes turn may include the batching window plus a slow model
    // response. The local live smoke observed a canonical reply after 43s;
    // 120s keeps the UI waiting without turning a slow but valid turn into a
    // synthetic transport failure.
    this.maxWaitMs = options.maxWaitMs ?? ConversationTracker.DEFAULT_MAX_WAIT_MS;
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
    return this.submit(payload, contactAlias, true);
  }

  async retry(contactAlias: string, eventId: string): Promise<TurnReceipt | null> {
    const pending = this.getConversationState(contactAlias)?.optimisticMessages
      .find((candidate) => candidate.eventId === eventId);
    if (!pending) {
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
    }, contactAlias, false);
  }

  async sync(contactAlias: string): Promise<void> {
    if (this.disposed || this.inFlight.has(contactAlias)) {
      return;
    }
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
    this.startedAt.set(contactAlias, Date.now());
    await this.pollOnce(contactAlias, true);
  }

  stop(contactAlias: string): void {
    const timer = this.timers.get(contactAlias);
    if (timer !== undefined) {
      globalThis.clearTimeout(timer);
      this.timers.delete(contactAlias);
    }
    this.startedAt.delete(contactAlias);
  }

  dispose(): void {
    this.disposed = true;
    this.pendingStarts.clear();
    for (const alias of this.timers.keys()) {
      this.stop(alias);
    }
  }

  private async submit(
    payload: SyntheticTextPayload,
    contactAlias: string,
    allowAutomaticRetry: boolean,
  ): Promise<TurnReceipt | null> {
    let automaticAttempt = 0;
    while (true) {
      try {
        const receipt = await this.api.sendTextMessage(contactAlias, payload);
        this.dispatch({
          type: 'RECEIPT_RECEIVED',
          alias: contactAlias,
          eventId: payload.eventId,
          correlationId: receipt.correlationId,
          status: receipt.status,
        });
        // React state updates are scheduled, so the receipt may arrive before
        // getConversationState observes the optimistic pending item. Force the
        // first projection read after an accepted receipt; later cycles stop
        // normally once the canonical turn is complete.
        await this.startPolling(contactAlias, true);
        return receipt;
      } catch (error) {
        const retryable = error instanceof ConversationHttpError ? error.retryable : true;
        if (allowAutomaticRetry && retryable && automaticAttempt === 0) {
          automaticAttempt += 1;
          const current = this.getConversationState(contactAlias)?.optimisticMessages
            .find((pending) => pending.eventId === payload.eventId);
          this.dispatch({
            type: 'RETRY_STARTED',
            alias: contactAlias,
            eventId: payload.eventId,
            attempts: (current?.attempts ?? 1) + 1,
          });
          continue;
        }
        this.dispatch({
          type: 'SEND_FAILED',
          alias: contactAlias,
          eventId: payload.eventId,
          error: sanitizeError(error),
          retryable,
        });
        return null;
      }
    }
  }

  private async pollOnce(contactAlias: string, continuePolling: boolean): Promise<void> {
    if (this.disposed || this.inFlight.has(contactAlias)) {
      return;
    }
    this.inFlight.add(contactAlias);
    try {
      const projection = await this.api.getConversationProjection(contactAlias);
      this.dispatch({
        type: 'PROJECTION_RECEIVED',
        alias: contactAlias,
        projection,
        activeAlias: this.getActiveAlias(),
      });
    } catch (error) {
      this.dispatch({ type: 'SYNC_FAILED', alias: contactAlias, error: sanitizeError(error) });
    } finally {
      this.inFlight.delete(contactAlias);
    }

    if (this.pendingStarts.delete(contactAlias)) {
      await this.startPolling(contactAlias, true);
      return;
    }

    if (!continuePolling && hasPendingWork(this.getConversationState(contactAlias))) {
      await this.startPolling(contactAlias);
      return;
    }
    if (continuePolling) {
      this.scheduleNext(contactAlias);
    }
  }

  private scheduleNext(contactAlias: string): void {
    if (this.disposed || !hasPendingWork(this.getConversationState(contactAlias))) {
      this.stop(contactAlias);
      return;
    }
    const started = this.startedAt.get(contactAlias) ?? Date.now();
    if (Date.now() - started >= this.maxWaitMs) {
      this.dispatch({
        type: 'SYNC_FAILED',
        alias: contactAlias,
        error: 'A resposta demorou mais que o esperado. Tente novamente.',
      });
      this.stop(contactAlias);
      return;
    }
    const timer = globalThis.setTimeout(() => {
      this.timers.delete(contactAlias);
      void this.pollOnce(contactAlias, true);
    }, this.intervalMs);
    this.timers.set(contactAlias, timer);
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
