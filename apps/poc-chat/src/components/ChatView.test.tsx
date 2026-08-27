import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatView } from './ChatView';
import { createConversationUiState, type ConversationUiState } from '../state/conversationReducer';
import { turnSummary } from '../test/fixtures';

const alias = 'manual-11111111-1111-4111-8111-111111111111';
const contact = {
  contactAlias: alias,
  displayName: 'Cliente local',
  createdAt: '2026-08-06T12:00:00.000Z',
  lastOpenedAt: '2026-08-06T12:00:00.000Z',
  archived: false,
  lastReadMessageId: null,
};

function state(overrides: Partial<ConversationUiState> = {}): ConversationUiState {
  return {
    ...createConversationUiState(),
    ...overrides,
  };
}

describe('ChatView', () => {
  it('shows optimistic messages and processing without fabricating a response', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(
      <ChatView
        contact={contact}
        conversation={state({ processingState: 'WAITING', optimisticMessages: [{
          eventId: 'ui-22222222-2222-4222-8222-222222222222',
          contactAlias: alias,
          text: 'Oi',
          occurredAt: '2026-08-06T12:00:00.000Z',
          attempts: 1,
          state: 'WAITING',
          lastError: null,
          correlationId: null,
        }] })}
        onSend={onSend}
        onRetry={vi.fn()}
      />,
    );

    expect(screen.getByText('Oi')).toBeInTheDocument();
    expect(screen.getByText(/mensagem aceita.*aguardando processamento/i)).toBeInTheDocument();
    expect(screen.queryByText(/resposta da urba/i)).not.toBeInTheDocument();
    await user.type(screen.getByRole('textbox', { name: /mensagem/i }), 'mais');
    await user.keyboard('{Enter}');
    expect(onSend).toHaveBeenCalledWith('mais');
  });

  it('shows a technical retry action without attributing the failure to Urba', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <ChatView
        contact={contact}
        conversation={state({
          processingState: 'FAILED_SAFE_TO_RETRY',
          turn: turnSummary({ status: 'FAILED_SAFE_TO_RETRY', retryAllowed: true }),
          optimisticMessages: [{
            eventId: 'ui-22222222-2222-4222-8222-222222222222',
            contactAlias: alias,
            text: 'Mensagem original',
            occurredAt: '2026-08-06T12:00:00.000Z',
            attempts: 2,
            state: 'FAILED_SAFE_TO_RETRY',
            lastError: 'Falha de transporte',
            correlationId: null,
          }],
        })}
        onSend={vi.fn()}
        onRetry={onRetry}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(/problema técnico/i);
    expect(screen.queryByText(/urba:.*falha/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /tentar novamente/i }));
    expect(onRetry).toHaveBeenCalledWith('ui-22222222-2222-4222-8222-222222222222');
  });

  it.each([
    ['DELAYED', /resposta está demorando/i],
    ['RECONCILING', /confirmando o resultado/i],
  ] as const)('renders %s as a non-conversational status', (processingState, text) => {
    render(
      <ChatView
        contact={contact}
        conversation={state({
          processingState,
          turn: turnSummary({ status: processingState }),
          optimisticMessages: [],
        })}
        onSend={vi.fn()}
        onRetry={vi.fn()}
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent(text);
    expect(screen.queryByText(/resposta da urba/i)).not.toBeInTheDocument();
  });

  it('shows canonical handoff ack, complementary human ownership and local architect/test controls', async () => {
    const user = userEvent.setup();
    const onPocControl = vi.fn().mockResolvedValue(undefined);
    render(
      <ChatView
        contact={contact}
        conversation={state({
          mode: 'HUMAN',
          ownership: 'HUMAN',
          messages: [{
            id: 'handoff-ack',
            eventId: 'handoff-ack-event',
            correlationId: 'corr-1',
            contactId: `poc:${alias}`,
            direction: 'OUTBOUND',
            senderType: 'URBA',
            type: 'TEXT',
            text: 'Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.',
            createdAt: '2026-08-06T12:00:01.000Z',
          }],
          pocControls: {
            approvePaymentProof: true,
            recordHumanMessage: true,
            returnToUrba: true,
          },
          resumeStatus: 'SYNCHRONIZING',
          resumeId: 'resume-123',
        })}
        onSend={vi.fn()}
        onRetry={vi.fn()}
        onPocControl={onPocControl}
      />,
    );

    expect(screen.getByText(/encaminhar sua conversa para a arquiteta/i)).toBeInTheDocument();
    expect(screen.getByText(/aguardando atendimento/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /controles locais/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /ação da arquiteta\/teste/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /aprovar pagamento.*ação da arquiteta\/teste/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /registrar mensagem humana.*ação da arquiteta\/teste/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /devolver responsabilidade.*ação da arquiteta\/teste/i })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /mensagem humana.*ação da arquiteta\/teste/i })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /^mensagem$/i })).toBeDisabled();
    expect(screen.getByText(/retomada.*reconciliação/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /aprovar pagamento.*ação da arquiteta\/teste/i }));
    expect(onPocControl).toHaveBeenCalledWith('APPROVE_PAYMENT_PROOF', undefined);
    await user.type(screen.getByRole('textbox', { name: /mensagem humana.*ação da arquiteta\/teste/i }), 'Vou acompanhar por aqui.');
    await user.click(screen.getByRole('button', { name: /registrar mensagem humana.*ação da arquiteta\/teste/i }));
    expect(onPocControl).toHaveBeenCalledWith('RECORD_HUMAN_MESSAGE', 'Vou acompanhar por aqui.');
    await user.click(screen.getByRole('button', { name: /devolver responsabilidade.*ação da arquiteta\/teste/i }));
    expect(onPocControl).toHaveBeenCalledWith('RETURN_TO_URBA', undefined);
    expect(screen.queryByText(/paymentUrl|hermes|correlationId|stack/i)).not.toBeInTheDocument();
  });
});
