import type { PendingSend } from '../state/conversationReducer';

interface FailureStateProps {
  pending: PendingSend[];
  onRetry: (eventId: string) => void;
}

export function FailureState({ pending, onRetry }: FailureStateProps) {
  const retryable = pending.find((item) => item.state === 'FAILED_RETRYABLE');
  if (!retryable) {
    return null;
  }
  return (
    <div className="failure-state" role="alert">
      <div>
        <strong>Houve um problema técnico.</strong>
        <p>A mensagem continua visível. Você pode tentar acompanhar novamente.</p>
      </div>
      <button type="button" onClick={() => onRetry(retryable.eventId)}>
        Tentar novamente
      </button>
    </div>
  );
}
