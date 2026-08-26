import type { LocalContact } from '../state/contactStore';
import type { PocControlAction, ResumeStatus } from '../api/contracts';
import { getVisibleMessages, type ConversationUiState } from '../state/conversationReducer';
import { ArchitectControls } from './ArchitectControls';
import { FailureState } from './FailureState';
import { MessageBubble } from './MessageBubble';
import { MessageComposer } from './MessageComposer';

interface ChatViewProps {
  contact: LocalContact;
  conversation: ConversationUiState;
  onSend: (text: string) => void;
  onRetry: (eventId: string) => void;
  onPocControl?: (action: PocControlAction, decision?: string) => void | Promise<void>;
}

export function ChatView({ contact, conversation, onSend, onRetry, onPocControl }: ChatViewProps) {
  const visibleMessages = getVisibleMessages(conversation);
  const ownership = conversation.ownership
    ?? (conversation.mode === 'HUMAN' ? 'HUMAN' : null);
  const humanOwned = ownership === 'HUMAN' || conversation.mode === 'HUMAN';
  return (
    <section className="chat-view" aria-label={`Conversa com ${contact.displayName}`}>
      <header className="chat-view__header">
        <div className="contact-avatar contact-avatar--large" aria-hidden="true">
          {contact.displayName.charAt(0).toUpperCase()}
        </div>
        <div>
          <h1>{contact.displayName}</h1>
          <p aria-label={ownership === 'HUMAN' ? 'Responsabilidade: atendimento humano' : 'Responsabilidade: Urba'}>
            {ownership === 'HUMAN' ? 'Aguardando atendimento' : 'Conversa local'}
          </p>
        </div>
      </header>
      <div className="message-history" role="log" aria-live="polite" aria-label="Histórico da conversa">
        {visibleMessages.length === 0 ? (
          <div className="message-history__empty">
            <span className="message-history__spark" aria-hidden="true">✦</span>
            <p>Comece a conversa com a Urba.</p>
          </div>
        ) : visibleMessages.map((message) => <MessageBubble key={message.id} message={message} />)}
        {conversation.processingState === 'WAITING' ? (
          <p className="processing-indicator" role="status">Mensagem aceita. Aguardando processamento…</p>
        ) : null}
        {conversation.processingState === 'DELAYED' ? (
          <p className="processing-indicator processing-indicator--delayed" role="status">
            A resposta está demorando mais que o esperado. O acompanhamento continua…
          </p>
        ) : null}
        {conversation.processingState === 'RECONCILING' ? (
          <p className="processing-indicator processing-indicator--reconciling" role="status">
            Confirmando o resultado do processamento. O acompanhamento continua…
          </p>
        ) : null}
        {conversation.processingState === 'HUMAN' ? (
          <p className="processing-indicator" role="status">Esta conversa aguarda atendimento humano.</p>
        ) : null}
        {isResumePending(conversation.resumeStatus) ? (
          <p className="processing-indicator processing-indicator--reconciling" role="status">
            {isResumeReconciling(conversation.resumeStatus)
              ? 'A retomada está em reconciliação. O acompanhamento continua…'
              : 'A retomada está aguardando processamento. O acompanhamento continua…'}
          </p>
        ) : null}
      </div>
      {conversation.pocControls !== null && onPocControl !== undefined ? (
        <ArchitectControls
          ownership={ownership}
          availability={conversation.pocControls}
          onAction={onPocControl}
        />
      ) : null}
      <FailureState
        pending={conversation.optimisticMessages}
        processingState={conversation.processingState}
        turn={conversation.turn}
        onRetry={onRetry}
      />
      <MessageComposer onSend={onSend} disabled={humanOwned || conversation.processingState === 'HUMAN'} />
    </section>
  );
}

function isResumePending(status: ResumeStatus | null): boolean {
  return status === 'PENDING'
    || status === 'SYNCHRONIZING'
    || status === 'DECIDING'
    || status === 'WAITING'
    || status === 'RECONCILING';
}

function isResumeReconciling(status: ResumeStatus | null): boolean {
  return status === 'SYNCHRONIZING'
    || status === 'DECIDING'
    || status === 'RECONCILING';
}
