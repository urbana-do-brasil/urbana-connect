import {
  conversationReducer,
  createConversationState,
  getVisibleMessages,
  hasPendingWork,
} from './conversationReducer';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';
const EVENT_ID = 'ui-22222222-2222-4222-8222-222222222222';

describe('conversationReducer handoff contract', () => {
  it('stops the waiting indicator in HUMAN mode without inventing output', () => {
    const state = conversationReducer(createConversationState(), {
      type: 'SEND_STARTED',
      alias: ALIAS,
      pending: {
        eventId: EVENT_ID,
        contactAlias: ALIAS,
        text: 'Preciso de atendimento',
        occurredAt: '2026-08-06T12:00:00.000Z',
        attempts: 1,
        state: 'WAITING',
        lastError: null,
        correlationId: null,
      },
    });
    const handedOff = conversationReducer(state, {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: {
        contactId: `poc:${ALIAS}`,
        conversation: { mode: 'HUMAN' },
        messages: [{
          id: 'inbound-1',
          eventId: EVENT_ID,
          correlationId: 'corr-1',
          contactId: `poc:${ALIAS}`,
          direction: 'INBOUND',
          senderType: 'CONTACT',
          type: 'TEXT',
          text: 'Preciso de atendimento',
          createdAt: '2026-08-06T12:00:00.000Z',
        }],
      },
    });

    expect(handedOff[ALIAS]?.processingState).toBe('HUMAN');
    expect(handedOff[ALIAS]?.optimisticMessages[0]?.state).toBe('HUMAN');
    expect(getVisibleMessages(handedOff[ALIAS]!)).toHaveLength(1);
    expect(getVisibleMessages(handedOff[ALIAS]!).some((message) => message.senderType === 'URBA')).toBe(false);
  });

  it('preserves a canonical human response when it already exists', () => {
    const state = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: {
        contactId: `poc:${ALIAS}`,
        conversation: { mode: 'HUMAN' },
        messages: [{
          id: 'inbound-1', eventId: EVENT_ID, correlationId: 'corr-1', contactId: `poc:${ALIAS}`,
          direction: 'INBOUND', senderType: 'CONTACT', type: 'TEXT', text: 'Oi', createdAt: '2026-08-06T12:00:00.000Z',
        }, {
          id: 'human-1', eventId: 'human-event', correlationId: 'corr-1', contactId: `poc:${ALIAS}`,
          direction: 'OUTBOUND', senderType: 'HUMAN', type: 'TEXT', text: 'Vou encaminhar seu atendimento.', createdAt: '2026-08-06T12:00:01.000Z',
        }],
      },
    });

    expect(getVisibleMessages(state[ALIAS]!)).toHaveLength(2);
    expect(getVisibleMessages(state[ALIAS]!)[1]?.senderType).toBe('HUMAN');
  });

  it('keeps the canonical handoff ack visible and exposes legacy ownership/resume/control state separately', () => {
    const handedOff = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: {
        contactId: `poc:${ALIAS}`,
        conversation: {
          mode: 'HUMAN',
          ownership: 'HUMAN',
          resume: {
            status: 'RECONCILING',
            retryAllowed: false,
            failureClass: 'HUMAN_CONTEXT_PENDING',
          },
          pocControls: {
            approvePaymentProof: true,
            recordDecision: false,
            returnToUrba: true,
          },
        },
        messages: [{
          id: 'handoff-ack',
          eventId: 'handoff-ack-event',
          correlationId: 'corr-1',
          contactId: `poc:${ALIAS}`,
          direction: 'OUTBOUND',
          senderType: 'URBA',
          type: 'TEXT',
          text: 'Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.',
          createdAt: '2026-08-06T12:00:01.000Z',
        }],
        turn: null,
      },
    });

    expect(handedOff[ALIAS]).toMatchObject({
      mode: 'HUMAN',
      ownership: 'HUMAN',
      processingState: 'HUMAN',
      resume: {
        status: 'RECONCILING',
        retryAllowed: false,
        failureClass: 'HUMAN_CONTEXT_PENDING',
      },
      pocControls: {
        approvePaymentProof: true,
        recordHumanMessage: false,
        returnToUrba: true,
      },
    });
    expect(getVisibleMessages(handedOff[ALIAS]!)).toEqual([
      expect.objectContaining({
        senderType: 'URBA',
        text: expect.stringMatching(/encaminhar sua conversa/),
      }),
    ]);
    expect(hasPendingWork(handedOff[ALIAS])).toBe(false);
  });

  it('uses a resume reconciliation state for polling when no turn summary is present', () => {
    const state = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: {
        contactId: `poc:${ALIAS}`,
        conversation: {
          mode: 'AI',
          resume: {
            status: 'RECONCILING',
            retryAllowed: false,
            failureClass: 'AMBIGUOUS_TRANSPORT',
          },
        },
        messages: [],
        turn: null,
      },
    });

    expect(state[ALIAS]?.processingState).toBe('RECONCILING');
    expect(hasPendingWork(state[ALIAS])).toBe(true);
  });

  it('normalizes current top-level ownership/resume/control fields and keeps resume polling fail-closed', () => {
    const state = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: {
        contactId: `poc:${ALIAS}`,
        ownership: 'HUMAN',
        resumeStatus: 'SYNCHRONIZING',
        resumeId: 'resume-123',
        controlAvailability: {
          approvePaymentProof: true,
          recordHumanMessage: true,
          returnToUrba: true,
        },
        conversation: { mode: 'HUMAN', version: 7 },
        messages: [{
          id: 'handoff-ack',
          eventId: 'handoff-ack-event',
          correlationId: 'corr-1',
          contactId: `poc:${ALIAS}`,
          direction: 'OUTBOUND',
          senderType: 'URBA',
          type: 'TEXT',
          text: 'Vou encaminhar sua conversa para a arquiteta.',
          createdAt: '2026-08-06T12:00:01.000Z',
        }, {
          id: 'handoff-ack-duplicate-id',
          eventId: 'handoff-ack-event',
          correlationId: 'corr-1',
          contactId: `poc:${ALIAS}`,
          direction: 'OUTBOUND',
          senderType: 'URBA',
          type: 'TEXT',
          text: 'Vou encaminhar sua conversa para a arquiteta.',
          createdAt: '2026-08-06T12:00:01.000Z',
        }],
        turn: null,
      },
    });

    expect(state[ALIAS]).toMatchObject({
      mode: 'HUMAN',
      ownership: 'HUMAN',
      resumeStatus: 'SYNCHRONIZING',
      resumeId: 'resume-123',
      conversationVersion: 7,
      processingState: 'HUMAN',
      pocControls: {
        approvePaymentProof: true,
        recordHumanMessage: true,
        returnToUrba: true,
      },
    });
    expect(getVisibleMessages(state[ALIAS]!)).toHaveLength(1);
    expect(hasPendingWork(state[ALIAS])).toBe(true);
  });

  it('renders top-level resume reconciliation while Urba still owns the conversation', () => {
    const state = conversationReducer(createConversationState(), {
      type: 'PROJECTION_RECEIVED',
      alias: ALIAS,
      projection: {
        contactId: `poc:${ALIAS}`,
        ownership: 'URBA',
        resumeStatus: 'SYNCHRONIZING',
        resumeId: 'resume-123',
        controlAvailability: {
          approvePaymentProof: false,
          recordHumanMessage: false,
          returnToUrba: false,
        },
        conversation: { mode: 'AI', version: 8 },
        messages: [],
        turn: null,
      },
    });

    expect(state[ALIAS]?.processingState).toBe('RECONCILING');
    expect(state[ALIAS]?.resumeStatus).toBe('SYNCHRONIZING');
    expect(hasPendingWork(state[ALIAS])).toBe(true);
  });
});
