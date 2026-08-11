import {
  archiveContact,
  createContact,
  loadUiState,
  saveUiState,
  selectContact,
  type PersistedUiState,
} from './contactStore';

type MemoryStorage = Storage & {
  getItemMock: ReturnType<typeof vi.fn>;
  setItemMock: ReturnType<typeof vi.fn>;
};

function memoryStorage(initial: string | null = null): MemoryStorage {
  let value = initial;
  const getItemMock = vi.fn(() => value);
  const setItemMock = vi.fn((_key: string, next: string) => {
    value = next;
  });
  return {
    getItem: getItemMock,
    setItem: setItemMock,
    removeItem: vi.fn(() => {
      value = null;
    }),
    clear: vi.fn(),
    key: vi.fn(() => null),
    get length() {
      return value === null ? 0 : 1;
    },
    getItemMock,
    setItemMock,
  } as MemoryStorage;
}

const UUID_A = '11111111-1111-4111-8111-111111111111';
const UUID_B = '22222222-2222-4222-8222-222222222222';

describe('contactStore', () => {
  it('creates opaque stable aliases and keeps the display name local', () => {
    const first = createContact('  Ana da Urba  ', () => UUID_A);
    const second = createContact('Ana da Urba', () => UUID_B);

    expect(first).toMatchObject({
      contactAlias: `manual-${UUID_A}`,
      displayName: 'Ana da Urba',
      archived: false,
      lastReadMessageId: null,
    });
    expect(first.contactAlias).not.toContain(first.displayName);
    expect(second.contactAlias).not.toBe(first.contactAlias);
  });

  it('persists only versioned visual metadata and never transcript-like fields', () => {
    const storage = memoryStorage();
    const contact = createContact('Cliente local', () => UUID_A);
    const state: PersistedUiState = {
      schemaVersion: 1,
      contacts: [contact],
      activeContactAlias: contact.contactAlias,
    };

    saveUiState(state, storage);

    const serialized = String(storage.setItemMock.mock.calls[0]?.[1]);
    expect(serialized).toContain('Cliente local');
    expect(serialized).not.toMatch(/transcript|mensagem|token|Authorization|eventId|correlationId|payload/i);
    expect(JSON.parse(serialized)).toEqual(state);
  });

  it('discards invalid contacts individually and safely resets unknown schemas', () => {
    const valid = createContact('Válido', () => UUID_A);
    const storage = memoryStorage(JSON.stringify({
      schemaVersion: 1,
      contacts: [
        valid,
        { ...valid, contactAlias: 'manual-not-a-uuid' },
        { ...valid, contactAlias: `manual-${UUID_B}` },
      ],
      activeContactAlias: 'manual-not-a-uuid',
    }));

    expect(loadUiState(storage)).toEqual({
      schemaVersion: 1,
      contacts: [valid, { ...valid, contactAlias: `manual-${UUID_B}` }],
      activeContactAlias: valid.contactAlias,
    });

    storage.getItemMock.mockReturnValue(JSON.stringify({ schemaVersion: 99, contacts: [valid] }));
    expect(loadUiState(storage)).toEqual({ schemaVersion: 1, contacts: [], activeContactAlias: null });
  });

  it('archives and selects contacts locally without a remote delete operation', () => {
    const first = createContact('Primeiro', () => UUID_A);
    const second = createContact('Segundo', () => UUID_B);
    const initial: PersistedUiState = {
      schemaVersion: 1,
      contacts: [first, second],
      activeContactAlias: first.contactAlias,
    };

    const archived = archiveContact(initial, first.contactAlias);
    expect(archived.contacts.find((contact) => contact.contactAlias === first.contactAlias)?.archived).toBe(true);
    expect(archived.activeContactAlias).toBe(second.contactAlias);
    expect(selectContact(archived, first.contactAlias)).toBe(archived);
    expect(selectContact(archived, second.contactAlias).activeContactAlias).toBe(second.contactAlias);
  });
});
