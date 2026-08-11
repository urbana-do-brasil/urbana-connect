import { useState, type FormEvent } from 'react';
import type { LocalContact } from '../state/contactStore';

interface ConversationListProps {
  contacts: LocalContact[];
  activeAlias: string | null;
  unreadAliases: Set<string>;
  processingAliases: Set<string>;
  onCreate: (displayName: string) => void;
  onSelect: (alias: string) => void;
  onArchive: (alias: string) => void;
}

export function ConversationList({
  contacts,
  activeAlias,
  unreadAliases,
  processingAliases,
  onCreate,
  onSelect,
  onArchive,
}: ConversationListProps) {
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);

  function create(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    if (displayName.trim().length === 0) {
      setError('Informe um nome para o contato.');
      return;
    }
    onCreate(displayName);
    setDisplayName('');
    setError(null);
  }

  return (
    <aside className="conversation-list" aria-label="Contatos">
      <div className="conversation-list__heading">
        <div>
          <p className="eyebrow">Conversas</p>
          <h2>Seus contatos</h2>
        </div>
      </div>
      <form className="new-contact" onSubmit={create}>
        <label htmlFor="contact-name" className="sr-only">Nome do contato</label>
        <input
          id="contact-name"
          aria-label="Nome do contato"
          value={displayName}
          onChange={(event) => {
            setDisplayName(event.target.value);
            setError(null);
          }}
          placeholder="Nome do contato"
          maxLength={80}
        />
        <button type="submit">Criar contato</button>
        {error !== null ? <p className="form-error" role="alert">{error}</p> : null}
      </form>
      <ul className="conversation-list__items">
        {contacts.length === 0 ? <li className="conversation-list__empty">Crie um contato para começar.</li> : null}
        {contacts.filter((contact) => !contact.archived).map((contact) => {
          const unread = unreadAliases.has(contact.contactAlias);
          const processing = processingAliases.has(contact.contactAlias);
          return (
            <li key={contact.contactAlias} className="conversation-list__item">
              <button
                type="button"
                className={`contact-button ${activeAlias === contact.contactAlias ? 'contact-button--active' : ''}`}
                onClick={() => onSelect(contact.contactAlias)}
                aria-current={activeAlias === contact.contactAlias ? 'page' : undefined}
              >
                <span className="contact-avatar" aria-hidden="true">{contact.displayName.charAt(0).toUpperCase()}</span>
                <span className="contact-button__copy">
                  <span className="contact-button__name">{contact.displayName}</span>
                  {processing ? <span className="contact-button__status">Processando…</span> : null}
                </span>
                {unread ? <span className="unread-dot" aria-label="Não lidas" title="Não lidas" /> : null}
              </button>
              <button
                type="button"
                className="archive-button"
                aria-label="Ocultar contato"
                onClick={() => onArchive(contact.contactAlias)}
              >
                Ocultar
              </button>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}
