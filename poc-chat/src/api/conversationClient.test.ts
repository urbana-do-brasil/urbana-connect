import {
  ConversationClient,
  ConversationHttpError,
  type FetchImplementation,
} from './conversationClient';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
const EVENT_ID = 'ui-22222222-2222-4222-8222-222222222222';
const OCCURRED_AT = '2026-08-06T12:00:00.000Z';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function projection() {
  return {
    contactId: `poc:${ALIAS}`,
    conversation: {},
    messages: [],
  };
}

describe('ConversationClient', () => {
  it('sends only the contract payload through a relative same-origin path', async () => {
    const fetchMock = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse({
      eventId: EVENT_ID,
      correlationId: 'corr-1',
      status: 'QUEUED',
    }, 202));
    const client = new ConversationClient(fetchMock);

    await client.sendTextMessage(ALIAS, {
      eventId: EVENT_ID,
      type: 'TEXT',
      text: 'Oi\nTudo bem?',
      occurredAt: OCCURRED_AT,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/poc/conversations/${ALIAS}/messages`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          eventId: EVENT_ID,
          type: 'TEXT',
          text: 'Oi\nTudo bem?',
          occurredAt: OCCURRED_AT,
        }),
      }),
    );
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(JSON.stringify(init)).not.toContain('Authorization');
  });

  it('treats 202 and 409 as receipts that continue projection tracking', async () => {
    const receipt = { eventId: EVENT_ID, correlationId: 'corr-1', status: 'DUPLICATE' };
    const fetchMock = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse(receipt, 409));
    const client = new ConversationClient(fetchMock);

    await expect(client.sendTextMessage(ALIAS, {
      eventId: EVENT_ID,
      type: 'TEXT',
      text: 'Oi',
      occurredAt: OCCURRED_AT,
    })).resolves.toMatchObject(receipt);
  });

  it('reads only the canonical projection and rejects a projection for another contact', async () => {
    const fetchMock = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse(projection()));
    const client = new ConversationClient(fetchMock);

    await expect(client.getConversationProjection(ALIAS)).resolves.toEqual(projection());
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/poc/conversations/${ALIAS}`,
      expect.objectContaining({ method: 'GET' }),
    );

    fetchMock.mockResolvedValueOnce(jsonResponse({ ...projection(), contactId: 'poc-other' }));
    await expect(client.getConversationProjection(ALIAS)).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });

  it.each([502, 504])('marks upstream %s failures as retryable transport errors', async (status) => {
    const fetchMock = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse({}, status));
    const client = new ConversationClient(fetchMock);

    const error = await client.sendTextMessage(ALIAS, {
      eventId: EVENT_ID,
      type: 'TEXT',
      text: 'Oi',
      occurredAt: OCCURRED_AT,
    }).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ConversationHttpError);
    expect(error).toMatchObject({ status, retryable: true });
  });

  it('has no client operation for flush or technical endpoints', () => {
    const client = new ConversationClient(vi.fn<FetchImplementation>());
    expect(client).not.toHaveProperty('flush');
    expect(client).not.toHaveProperty('getMetrics');
    expect(client).not.toHaveProperty('approvePaymentProof');
  });
});
