import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FailureState } from './FailureState';
import { turnSummary } from '../test/fixtures';

const eventId = 'ui-22222222-2222-4222-8222-222222222222';

describe('FailureState', () => {
  it('shows a technical retry without inventing a Urba message', async () => {
    const onRetry = vi.fn();
    render(<FailureState pending={[{
      eventId,
      contactAlias: 'manual-11111111-1111-4111-8111-111111111111',
      text: 'Mensagem original',
      occurredAt: '2026-08-06T12:00:00.000Z',
      attempts: 2,
      state: 'FAILED_SAFE_TO_RETRY',
      lastError: 'timeout',
      correlationId: null,
    }]} processingState="FAILED_SAFE_TO_RETRY" turn={turnSummary({ status: 'FAILED_SAFE_TO_RETRY', retryAllowed: true })} onRetry={onRetry} />);

    expect(screen.getByRole('alert')).toHaveTextContent(/problema técnico/i);
    expect(screen.queryByText(/urba.*falha/i)).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: /tentar novamente/i }));
    expect(onRetry).toHaveBeenCalledWith(eventId);
  });

  it('does not render when there is no retryable failure', () => {
    const { container } = render(<FailureState pending={[]} processingState="IDLE" turn={null} onRetry={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows terminal technical failure without offering an unsafe retry', () => {
    render(<FailureState pending={[{
      eventId,
      contactAlias: 'manual-11111111-1111-4111-8111-111111111111',
      text: 'Mensagem original',
      occurredAt: '2026-08-06T12:00:00.000Z',
      attempts: 1,
      state: 'FAILED_TERMINAL',
      lastError: 'falha terminal',
      correlationId: null,
    }]} processingState="FAILED_TERMINAL" turn={turnSummary({ status: 'FAILED_TERMINAL' })} onRetry={vi.fn()} />);

    expect(screen.getByRole('alert')).toHaveTextContent(/problema técnico/i);
    expect(screen.queryByRole('button', { name: /tentar novamente/i })).not.toBeInTheDocument();
  });
});
