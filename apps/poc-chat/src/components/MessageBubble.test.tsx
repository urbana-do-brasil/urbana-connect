import { render, screen } from '@testing-library/react';
import { MessageBubble } from './MessageBubble';
import type { CanonicalMessage } from '../api/contracts';
import '../styles.css';

const base: CanonicalMessage = {
  id: 'message-1',
  eventId: 'event-1',
  correlationId: 'corr-1',
  contactId: 'poc:manual-11111111-1111-4111-8111-111111111111',
  direction: 'OUTBOUND',
  senderType: 'URBA',
  type: 'TEXT',
  text: 'linha 1\nlinha 2 https://example.com/<script> javascript:alert(1)',
  createdAt: '2026-08-06T12:00:00.000Z',
};

describe('MessageBubble', () => {
  it('preserves line breaks and creates only safe http/https links', () => {
    render(<MessageBubble message={base} />);

    expect(screen.getByText('linha 1')).toBeInTheDocument();
    expect(screen.getByText(/linha 2/)).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /https:\/\/example\.com/i });
    expect(link).toHaveAttribute('href', expect.stringMatching(/^https:\/\/example\.com/));
    expect(screen.queryByRole('link', { name: /javascript/i })).not.toBeInTheDocument();
    expect(screen.getByText(/javascript:alert/)).toBeInTheDocument();
  });

  it('renders message text as text rather than executable markup', () => {
    render(<MessageBubble message={{ ...base, text: '<strong>não executar</strong>' }} />);

    expect(screen.getByText('<strong>não executar</strong>')).toBeInTheDocument();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('keeps Hermes whitespace visible in the message content', () => {
    const text = '  Hermes  mantém  espaços  \n  e termina assim  ';
    const { container } = render(<MessageBubble message={{ ...base, text }} />);
    const content = container.querySelector('.message-bubble__content');

    expect(content).not.toBeNull();
    expect(content).toHaveStyle({ whiteSpace: 'pre-wrap' });
    expect(content?.querySelectorAll('br')).toHaveLength(1);
    expect(content?.firstElementChild?.textContent).toBe('  Hermes  mantém  espaços  ');
    expect(content?.lastElementChild?.textContent).toBe('  e termina assim  ');
  });
});
