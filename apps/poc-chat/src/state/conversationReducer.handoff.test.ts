import {
  conversationReducer,
  createConversationState,
  getVisibleMessages,
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
});
