# Contract: POC conversation projection and receipt

## POST `/api/poc/conversations/{contactAlias}/messages`

The endpoint remains local and synthetic. After validating and durably accepting
the event, it returns `202 Accepted` without waiting for Hermes.

```json
{
  "eventId": "ui-<uuid>",
  "correlationId": "opaque-correlation",
  "status": "QUEUED",
  "output": null,
  "error": null
}
```

The same `eventId` is the idempotency key. A transport timeout at the browser
must be followed by projection lookup, not by generating a new event ID or
automatically dispatching a second remote turn.

## GET `/api/poc/conversations/{contactAlias}`

The existing `contactId`, `conversation` and canonical `messages` fields remain
compatible. The response adds a safe `turn` summary:

```json
{
  "contactId": "poc:manual-<uuid>",
  "conversation": {},
  "messages": [],
  "turn": {
    "status": "QUEUED|RUNNING|DELAYED|RECONCILING|COMPLETED|FAILED_SAFE_TO_RETRY|FAILED_TERMINAL|BLOCKED_BY_HUMAN",
    "correlationId": "opaque-correlation",
    "attempt": 1,
    "retryAllowed": false,
    "failureClass": null,
    "acceptedAt": "2026-08-07T12:00:00Z",
    "startedAt": null,
    "finishedAt": null
  }
}
```

`turn` can be `null` for a new contact. The projection MUST NOT expose a Hermes
session ID, provider payload, secret, raw exception or full prompt.

## Frontend interpretation

- `QUEUED`, `RUNNING`, `DELAYED`, `RECONCILING`: keep polling and show a
  non-conversational waiting state.
- `COMPLETED`: display the canonical outbound message exactly once.
- `FAILED_SAFE_TO_RETRY`: show technical failure and enable retry for the same
  event ID.
- `FAILED_TERMINAL` or `BLOCKED_BY_HUMAN`: do not invent Urba text; show the
  appropriate non-conversational state.
- A failed GET is a synchronization problem, not evidence that the turn failed;
  polling may back off and retry.
