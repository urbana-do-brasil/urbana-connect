import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConversationList } from './ConversationList';
import type { LocalContact } from '../state/contactStore';

const contacts: LocalContact[] = [
  {
    contactAlias: 'manual-11111111-1111-4111-8111-111111111111',
    displayName: 'Mesmo nome',
    createdAt: '2026-08-06T12:00:00.000Z',
    lastOpenedAt: '2026-08-06T12:00:00.000Z',
    archived: false,
    lastReadMessageId: null,
  },
  {
    contactAlias: 'manual-22222222-2222-4222-8222-222222222222',
    displayName: 'Mesmo nome',
    createdAt: '2026-08-06T12:01:00.000Z',
    lastOpenedAt: '2026-08-06T12:01:00.000Z',
    archived: false,
    lastReadMessageId: null,
  },
];

describe('ConversationList', () => {
  it('creates, selects, and archives contacts while showing unread state', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const onSelect = vi.fn();
    const onArchive = vi.fn();
    render(
      <ConversationList
        contacts={contacts}
        activeAlias={contacts[0].contactAlias}
        unreadAliases={new Set([contacts[1].contactAlias])}
        processingAliases={new Set()}
        onCreate={onCreate}
        onSelect={onSelect}
        onArchive={onArchive}
      />,
    );

    expect(screen.getAllByText('Mesmo nome')).toHaveLength(2);
    expect(screen.getByLabelText(/não lidas/i)).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: /mesmo nome/i })[1]);
    expect(onSelect).toHaveBeenCalledWith(contacts[1].contactAlias);
    await user.click(screen.getAllByRole('button', { name: /ocultar/i })[0]);
    expect(onArchive).toHaveBeenCalledWith(contacts[0].contactAlias);

    await user.type(screen.getByRole('textbox', { name: /nome do contato/i }), 'Novo contato');
    await user.click(screen.getByRole('button', { name: /criar contato/i }));
    expect(onCreate).toHaveBeenCalledWith('Novo contato');
  });
});
