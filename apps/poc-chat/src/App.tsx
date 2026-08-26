import { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { ConversationClient } from './api/conversationClient';
import { createIdempotencyKey } from './api/contracts';
import type { PocControlAction, PocControlAvailability } from './api/contracts';
import { ChatView } from './components/ChatView';
import { ConversationList } from './components/ConversationList';
import {
  archiveContact,
  createContact,
  loadUiState,
  saveUiState,
  selectContact,
  type PersistedUiState,
} from './state/contactStore';
import {
  conversationReducer,
  createConversationState,
  createConversationUiState,
  type ConversationUiState,
  type ConversationState,
} from './state/conversationReducer';
import { ConversationTracker } from './state/conversationTracker';

export default function App() {
  const [uiState, setUiState] = useState<PersistedUiState>(() => normalizeInitialUiState(loadUiState()));
  const [conversationState, dispatch] = useReducer(conversationReducer, createConversationState());
  const conversationRef = useRef<ConversationState>(conversationState);
  const activeAliasRef = useRef<string | null>(uiState.activeContactAlias);
  conversationRef.current = conversationState;
  activeAliasRef.current = uiState.activeContactAlias;

  const client = useMemo(() => new ConversationClient(), []);
  const tracker = useMemo(() => new ConversationTracker({
    api: client,
    dispatch,
    getConversationState: (alias) => conversationRef.current[alias],
    getActiveAlias: () => activeAliasRef.current,
  }), [client, dispatch]);

  const visibleAliases = useMemo(
    () => uiState.contacts.filter((contact) => !contact.archived).map((contact) => contact.contactAlias),
    [uiState.contacts],
  );
  const aliasKey = visibleAliases.join('|');

  useEffect(() => {
    for (const alias of visibleAliases) {
      void tracker.sync(alias);
    }
  }, [aliasKey, tracker, visibleAliases]);

  useEffect(() => () => tracker.dispose(), [tracker]);

  function persist(next: PersistedUiState): void {
    setUiState(next);
    activeAliasRef.current = next.activeContactAlias;
    saveUiState(next);
  }

  function handleCreate(displayName: string): void {
    const contact = createContact(displayName);
    const next: PersistedUiState = {
      schemaVersion: 1,
      contacts: [...uiState.contacts, contact],
      activeContactAlias: contact.contactAlias,
    };
    persist(next);
    void tracker.sync(contact.contactAlias);
  }

  function handleSelect(alias: string): void {
    const next = selectContact(uiState, alias);
    persist(next);
    dispatch({ type: 'MARK_READ', alias });
    void tracker.sync(alias);
  }

  function handleArchive(alias: string): void {
    if (!globalThis.confirm('Ocultar este contato da lista? O histórico remoto será preservado.')) {
      return;
    }
    const next = archiveContact(uiState, alias);
    persist(next);
    if (next.activeContactAlias !== null) {
      void tracker.sync(next.activeContactAlias);
    }
  }

  function handleSend(text: string): void {
    const alias = uiState.activeContactAlias;
    if (alias !== null) {
      void tracker.send(alias, text);
    }
  }

  function handleRetry(eventId: string): void {
    const alias = uiState.activeContactAlias;
    if (alias !== null) {
      void tracker.retry(alias, eventId);
    }
  }

  async function handlePocControl(action: PocControlAction, humanMessage?: string): Promise<void> {
    const alias = uiState.activeContactAlias;
    const conversation = alias === null ? undefined : conversationRef.current[alias];
    if (alias === null || conversation === undefined) {
      throw new Error('Ação local indisponível.');
    }
    if (action === 'APPROVE_PAYMENT_PROOF') {
      if (typeof client.approvePaymentProof !== 'function') {
        throw new Error('Ação local indisponível.');
      }
      await client.approvePaymentProof(alias);
    } else if (action === 'RECORD_HUMAN_MESSAGE') {
      if (conversation.ownership !== 'HUMAN'
          || typeof client.recordHumanMessage !== 'function'
          || humanMessage === undefined
          || humanMessage.trim().length === 0) {
        throw new Error('Ação local indisponível.');
      }
      await client.recordHumanMessage(alias, createIdempotencyKey(), {
        text: humanMessage.trim(),
        occurredAt: new Date().toISOString(),
      });
    } else {
      if (conversation.ownership !== 'HUMAN'
          || conversation.conversationVersion === null
          || typeof client.returnToUrba !== 'function') {
        throw new Error('Ação local indisponível.');
      }
      await client.returnToUrba(
        alias,
        createIdempotencyKey(),
        conversation.conversationVersion,
      );
    }
    await tracker.sync(alias);
  }

  const contacts = uiState.contacts.filter((contact) => !contact.archived);
  const activeContact = contacts.find((contact) => contact.contactAlias === uiState.activeContactAlias) ?? null;
  const activeConversation = activeContact === null
    ? null
    : conversationState[activeContact.contactAlias] ?? createConversationUiState();
  const activePocControls = activeConversation === null
    ? null
    : availablePocControls(activeConversation, client);
  const unreadAliases = new Set(
    Object.entries(conversationState)
      .filter(([, state]) => state.unread)
      .map(([alias]) => alias),
  );
  const processingAliases = new Set(
    Object.entries(conversationState)
      .filter(([, state]) => state.processingState === 'WAITING'
        || state.processingState === 'DELAYED'
        || state.processingState === 'RECONCILING')
      .map(([alias]) => alias),
  );

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-mark" aria-label="Urba">
          <span className="brand-mark__symbol" aria-hidden="true">u</span>
          <span className="brand-mark__name">urba</span>
        </div>
        <span className="local-badge">Simulador local</span>
      </header>
      <div className="app-body">
        <ConversationList
          contacts={uiState.contacts}
          activeAlias={uiState.activeContactAlias}
          unreadAliases={unreadAliases}
          processingAliases={processingAliases}
          onCreate={handleCreate}
          onSelect={handleSelect}
          onArchive={handleArchive}
        />
        {activeContact ? (
          <ChatView
            contact={activeContact}
            conversation={{ ...activeConversation!, pocControls: activePocControls }}
            onSend={handleSend}
            onRetry={handleRetry}
            onPocControl={handlePocControl}
          />
        ) : (
          <section className="welcome-panel" aria-label="Boas-vindas">
            <div className="welcome-panel__orb" aria-hidden="true">✦</div>
            <h1>Converse com a Urba</h1>
            <p>Crie um contato local para começar a testar uma conversa.</p>
          </section>
        )}
      </div>
    </div>
  );
}

function availablePocControls(
  conversation: ConversationUiState,
  client: ConversationClient,
): PocControlAvailability | null {
  if (conversation.pocControls === null) {
    return null;
  }
  const humanOwned = conversation.ownership === 'HUMAN' || conversation.mode === 'HUMAN';
  return {
    approvePaymentProof: conversation.pocControls.approvePaymentProof
      && typeof client.approvePaymentProof === 'function',
    recordHumanMessage: humanOwned
      && conversation.pocControls.recordHumanMessage
      && typeof client.recordHumanMessage === 'function',
    returnToUrba: humanOwned
      && conversation.pocControls.returnToUrba
      && conversation.conversationVersion !== null
      && typeof client.returnToUrba === 'function',
  };
}

function normalizeInitialUiState(state: PersistedUiState): PersistedUiState {
  if (state.activeContactAlias !== null
      && state.contacts.some((contact) => contact.contactAlias === state.activeContactAlias && !contact.archived)) {
    return state;
  }
  return {
    ...state,
    activeContactAlias: state.contacts.find((contact) => !contact.archived)?.contactAlias ?? null,
  };
}
