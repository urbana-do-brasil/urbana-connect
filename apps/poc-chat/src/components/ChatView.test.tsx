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
});
