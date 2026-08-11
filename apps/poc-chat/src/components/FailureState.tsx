import type { TurnSummary } from '../api/contracts';
import type { PendingSend, ProcessingState } from '../state/conversationReducer';

interface FailureStateProps {
  pending: PendingSend[];
  processingState: ProcessingState;
  turn: TurnSummary | null;
  onRetry: (eventId: string) => void;
}

export function FailureState({ pending, processingState, turn, onRetry }: FailureStateProps) {
  const safeToRetry = processingState === 'FAILED_SAFE_TO_RETRY'
    && turn?.status === 'FAILED_SAFE_TO_RETRY'
    && turn.retryAllowed === true;
  const showTerminalFailure = processingState === 'FAILED_TERMINAL'
    || (processingState === 'FAILED_SAFE_TO_RETRY' && !safeToRetry);
  if (!safeToRetry && !showTerminalFailure) {
    return null;
  }
  const retryable = safeToRetry
    ? pending.find((item) => item.state === 'FAILED_SAFE_TO_RETRY')
    : undefined;
  return (
    <div className="failure-state" role="alert">
      <div>
        <strong>Houve um problema técnico no processamento.</strong>
        <p>
          {retryable
            ? 'A mensagem original continua visível. Uma nova tentativa foi autorizada pelo serviço.'
            : 'A mensagem original continua visível. Nenhuma nova tentativa automática será feita.'}
        </p>
      </div>
      {retryable ? (
        <button type="button" onClick={() => onRetry(retryable.eventId)}>
          Tentar novamente
        </button>
      ) : null}
    </div>
  );
}
