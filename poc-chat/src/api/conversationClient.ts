import {
  MAX_TEXT_LENGTH,
  canonicalContactId,
  isContactAlias,
  isEventId,
  parseConversationProjection,
  parseTurnReceipt,
  type ConversationProjection,
  type SyntheticTextPayload,
  type TurnReceipt,
} from './contracts';

export type FetchImplementation = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response>;

export interface ConversationApi {
  sendTextMessage(contactAlias: string, payload: SyntheticTextPayload): Promise<TurnReceipt>;
  getConversationProjection(contactAlias: string): Promise<ConversationProjection>;
}

export type ConversationHttpErrorKind = 'transport' | 'http' | 'invalid-response';

export class ConversationHttpError extends Error {
  readonly status: number | null;
  readonly retryable: boolean;
  readonly kind: ConversationHttpErrorKind;

  constructor(
    message: string,
    options: {
      status?: number | null;
      retryable?: boolean;
      kind?: ConversationHttpErrorKind;
    } = {},
  ) {
    super(message);
    this.name = 'ConversationHttpError';
    this.status = options.status ?? null;
    this.retryable = options.retryable ?? false;
    this.kind = options.kind ?? 'http';
  }
}

export class ConversationClient implements ConversationApi {
  private readonly fetchImpl: FetchImplementation;
  private readonly timeoutMs: number;

  constructor(
    fetchImpl: FetchImplementation = (input, init) => fetch(input, init),
    options: { timeoutMs?: number } = {},
  ) {
    this.fetchImpl = fetchImpl;
    this.timeoutMs = options.timeoutMs ?? 15_000;
  }

  async sendTextMessage(contactAlias: string, payload: SyntheticTextPayload): Promise<TurnReceipt> {
    validateAlias(contactAlias);
    validatePayload(payload);
    const response = await this.request(
      `/api/poc/conversations/${encodeURIComponent(contactAlias)}/messages`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      },
    );
    try {
      return parseTurnReceipt(await this.readJson(response), payload.eventId);
    } catch (error) {
      if (error instanceof ConversationHttpError) {
        throw error;
      }
      throw new ConversationHttpError('O serviço local retornou um recibo inválido.', {
        status: response.status,
        kind: 'invalid-response',
      });
    }
  }

  async getConversationProjection(contactAlias: string): Promise<ConversationProjection> {
    validateAlias(contactAlias);
    const response = await this.request(
      `/api/poc/conversations/${encodeURIComponent(contactAlias)}`,
      { method: 'GET', credentials: 'same-origin' },
    );
    try {
      return parseConversationProjection(
        await this.readJson(response),
        canonicalContactId(contactAlias),
      );
    } catch (error) {
      if (error instanceof ConversationHttpError) {
        throw error;
      }
      throw new ConversationHttpError('O serviço local retornou uma projeção inválida.', {
        status: response.status,
        kind: 'invalid-response',
      });
    }
  }

  private async request(path: string, init: RequestInit): Promise<Response> {
    const controller = new AbortController();
    const timeout = globalThis.setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await this.fetchImpl(path, { ...init, signal: controller.signal });
      if (response.ok || response.status === 409) {
        return response;
      }
      throw new ConversationHttpError(messageForStatus(response.status), {
        status: response.status,
        retryable: response.status === 502 || response.status === 504,
        kind: 'http',
      });
    } catch (error) {
      if (error instanceof ConversationHttpError) {
        throw error;
      }
      const isTimeout = error instanceof DOMException && error.name === 'AbortError';
      throw new ConversationHttpError(
        isTimeout ? 'O tempo de resposta do serviço local foi excedido.' : 'Não foi possível alcançar o serviço local.',
        { retryable: true, kind: 'transport' },
      );
    } finally {
      globalThis.clearTimeout(timeout);
    }
  }

  private async readJson(response: Response): Promise<unknown> {
    try {
      return await response.json() as unknown;
    } catch {
      throw new ConversationHttpError('O serviço local retornou uma resposta inválida.', {
        status: response.status,
        retryable: false,
        kind: 'invalid-response',
      });
    }
  }
}

function validateAlias(alias: string): void {
  if (!isContactAlias(alias)) {
    throw new ConversationHttpError('O contato local é inválido.', {
      kind: 'invalid-response',
    });
  }
}

function validatePayload(payload: SyntheticTextPayload): void {
  if (!isEventId(payload.eventId)
      || payload.type !== 'TEXT'
      || typeof payload.text !== 'string'
      || payload.text.trim().length === 0
      || payload.text.length > MAX_TEXT_LENGTH
      || Number.isNaN(Date.parse(payload.occurredAt))) {
    throw new ConversationHttpError('A mensagem textual é inválida.', {
      kind: 'invalid-response',
    });
  }
}

function messageForStatus(status: number): string {
  if (status === 502 || status === 504) {
    return 'O serviço local está temporariamente indisponível.';
  }
  if (status === 401) {
    return 'A configuração local do simulador não está disponível.';
  }
  return 'A mensagem não foi aceita pelo serviço local.';
}
