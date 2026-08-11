import {
  createContactAlias,
  isContactAlias,
  isIsoDate,
} from '../api/contracts';

export const STORAGE_KEY = 'urbana.poc-chat.v1';
export const SCHEMA_VERSION = 1;
export const MAX_CONTACTS = 50;
export const MAX_DISPLAY_NAME_LENGTH = 80;

export interface LocalContact {
  contactAlias: string;
  displayName: string;
  createdAt: string;
  lastOpenedAt: string;
  archived: boolean;
  lastReadMessageId: string | null;
}

export interface PersistedUiState {
  schemaVersion: 1;
  contacts: LocalContact[];
  activeContactAlias: string | null;
}

export class ContactValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ContactValidationError';
  }
}

export function createEmptyUiState(): PersistedUiState {
  return { schemaVersion: SCHEMA_VERSION, contacts: [], activeContactAlias: null };
}

export function createContact(
  displayName: string,
  uuidFactory: () => string = () => crypto.randomUUID(),
  now: () => string = () => new Date().toISOString(),
): LocalContact {
  const normalizedName = displayName.trim();
  if (normalizedName.length === 0) {
    throw new ContactValidationError('Informe um nome para o contato.');
  }
  if (normalizedName.length > MAX_DISPLAY_NAME_LENGTH) {
    throw new ContactValidationError(`O nome deve ter no máximo ${MAX_DISPLAY_NAME_LENGTH} caracteres.`);
  }
  const timestamp = now();
  return {
    contactAlias: createContactAlias(uuidFactory),
    displayName: normalizedName,
    createdAt: timestamp,
    lastOpenedAt: timestamp,
    archived: false,
    lastReadMessageId: null,
  };
}

export function loadUiState(storage: Storage = browserStorage()): PersistedUiState {
  let raw: string | null;
  try {
    raw = storage.getItem(STORAGE_KEY);
  } catch {
    return createEmptyUiState();
  }
  if (raw === null) {
    return createEmptyUiState();
  }

  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isRecord(parsed) || parsed.schemaVersion !== SCHEMA_VERSION || !Array.isArray(parsed.contacts)) {
      return createEmptyUiState();
    }
    const contacts = uniqueContacts(parsed.contacts.filter(isLocalContact)).slice(0, MAX_CONTACTS);
    const active = typeof parsed.activeContactAlias === 'string' ? parsed.activeContactAlias : null;
    return {
      schemaVersion: SCHEMA_VERSION,
      contacts,
      activeContactAlias: contacts.some((contact) => contact.contactAlias === active && !contact.archived)
        ? active
        : firstVisibleAlias(contacts),
    };
  } catch {
    return createEmptyUiState();
  }
}

export function saveUiState(state: PersistedUiState, storage: Storage = browserStorage()): void {
  const safeState: PersistedUiState = {
    schemaVersion: SCHEMA_VERSION,
    contacts: uniqueContacts(state.contacts.filter(isLocalContact)).slice(0, MAX_CONTACTS),
    activeContactAlias: null,
  };
  safeState.activeContactAlias = safeState.contacts.some(
    (contact) => contact.contactAlias === state.activeContactAlias && !contact.archived,
  ) ? state.activeContactAlias : firstVisibleAlias(safeState.contacts);
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(safeState));
  } catch {
    // localStorage is optional visual state; the canonical transcript remains remote.
  }
}

export function selectContact(state: PersistedUiState, alias: string, now: string = new Date().toISOString()): PersistedUiState {
  const contact = state.contacts.find((candidate) => candidate.contactAlias === alias && !candidate.archived);
  if (!contact) {
    return state;
  }
  return {
    ...state,
    contacts: state.contacts.map((candidate) => candidate.contactAlias === alias
      ? { ...candidate, lastOpenedAt: now }
      : candidate),
    activeContactAlias: alias,
  };
}

export function archiveContact(state: PersistedUiState, alias: string): PersistedUiState {
  const exists = state.contacts.some((contact) => contact.contactAlias === alias);
  if (!exists) {
    return state;
  }
  const contacts = state.contacts.map((contact) => contact.contactAlias === alias
    ? { ...contact, archived: true }
    : contact);
  return {
    ...state,
    contacts,
    activeContactAlias: state.activeContactAlias === alias
      ? firstVisibleAlias(contacts)
      : state.activeContactAlias,
  };
}

export function isLocalContact(value: unknown): value is LocalContact {
  if (!isRecord(value)
      || !isContactAlias(value.contactAlias)
      || typeof value.displayName !== 'string'
      || value.displayName.trim().length === 0
      || value.displayName.length > MAX_DISPLAY_NAME_LENGTH
      || !isIsoDate(value.createdAt)
      || !isIsoDate(value.lastOpenedAt)
      || typeof value.archived !== 'boolean'
      || (value.lastReadMessageId !== null && typeof value.lastReadMessageId !== 'string')) {
    return false;
  }
  return value.displayName === value.displayName.trim();
}

function uniqueContacts(contacts: LocalContact[]): LocalContact[] {
  const seen = new Set<string>();
  return contacts.filter((contact) => {
    if (seen.has(contact.contactAlias)) {
      return false;
    }
    seen.add(contact.contactAlias);
    return true;
  });
}

function firstVisibleAlias(contacts: LocalContact[]): string | null {
  return contacts.find((contact) => !contact.archived)?.contactAlias ?? null;
}

function browserStorage(): Storage {
  return globalThis.localStorage;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
