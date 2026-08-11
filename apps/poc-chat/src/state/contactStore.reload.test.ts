import {
  createContact,
  createEmptyUiState,
  loadUiState,
  saveUiState,
  STORAGE_KEY,
} from './contactStore';

const ALIAS = 'manual-11111111-1111-4111-8111-111111111111';

describe('contactStore reload contract', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('hydrates only valid visual metadata and preserves the active contact', () => {
    const contact = createContact('Cliente local', () => '11111111-1111-4111-8111-111111111111');
    saveUiState({
      schemaVersion: 1,
      contacts: [{ ...contact, contactAlias: ALIAS }],
      activeContactAlias: ALIAS,
    });

    const reloaded = loadUiState();

    expect(reloaded.activeContactAlias).toBe(ALIAS);
    expect(reloaded.contacts).toEqual([expect.objectContaining({
      contactAlias: ALIAS,
      displayName: 'Cliente local',
    })]);
    expect(localStorage.getItem(STORAGE_KEY)).not.toMatch(/transcript|eventId|correlationId|token/i);
  });

  it('discards an unknown schema instead of importing transcript-like data', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      schemaVersion: 99,
      contacts: [{ contactAlias: ALIAS, displayName: 'Não importar' }],
      transcript: [{ text: 'segredo' }],
    }));

    expect(loadUiState()).toEqual(createEmptyUiState());
  });

  it('deduplicates contacts by opaque alias and keeps archived contacts local', () => {
    const contact = createContact('Cliente local', () => '11111111-1111-4111-8111-111111111111');
    const state = {
      schemaVersion: 1 as const,
      contacts: [{ ...contact, contactAlias: ALIAS }, { ...contact, contactAlias: ALIAS, archived: true }],
      activeContactAlias: ALIAS,
    };

    saveUiState(state);

    expect(loadUiState().contacts).toHaveLength(1);
    expect(loadUiState().contacts[0]?.archived).toBe(false);
  });
});
