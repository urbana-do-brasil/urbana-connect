import { useState, type FormEvent } from 'react';
import type { ConversationOwnership, PocControlAction, PocControlAvailability } from '../api/contracts';

interface ArchitectControlsProps {
  ownership: ConversationOwnership | null;
  availability: PocControlAvailability;
  onAction: (action: PocControlAction, decision?: string) => void | Promise<void>;
}

const CONTROL_LABEL = 'ação da arquiteta/teste';

export function ArchitectControls({ ownership, availability, onAction }: ArchitectControlsProps) {
  const [decision, setDecision] = useState('');
  const [busyAction, setBusyAction] = useState<PocControlAction | null>(null);
  const [error, setError] = useState<string | null>(null);

  const hasControl = availability.approvePaymentProof
    || (ownership === 'HUMAN' && (availability.recordHumanMessage || availability.returnToUrba));
  if (!hasControl) {
    return null;
  }

  async function run(action: PocControlAction, value?: string): Promise<void> {
    setBusyAction(action);
    setError(null);
    try {
      await onAction(action, value);
      if (action === 'RECORD_HUMAN_MESSAGE') {
        setDecision('');
      }
    } catch {
      setError('A ação de teste não foi concluída. A conversa permanece sob responsabilidade humana.');
    } finally {
      setBusyAction(null);
    }
  }

  function submitDecision(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    const normalized = decision.trim();
    if (normalized.length === 0) {
      setError('Informe a mensagem humana de teste antes de registrar.');
      return;
    }
    void run('RECORD_HUMAN_MESSAGE', normalized);
  }

  return (
    <aside className="architect-controls" aria-label="Controles locais da arquiteta">
      <div className="architect-controls__heading">
        <h2>Controles locais — {CONTROL_LABEL}</h2>
        <p>Não envia mensagem do cliente, WhatsApp, e-mail ou pagamento real.</p>
      </div>
      <div className="architect-controls__actions">
        {availability.approvePaymentProof ? (
          <button
            type="button"
            disabled={busyAction !== null}
            onClick={() => void run('APPROVE_PAYMENT_PROOF')}
          >
            Aprovar pagamento — {CONTROL_LABEL}
          </button>
        ) : null}
        {ownership === 'HUMAN' && availability.recordHumanMessage ? (
          <form onSubmit={submitDecision} className="architect-controls__decision">
            <label htmlFor="architect-human-message">Mensagem humana — {CONTROL_LABEL}</label>
            <textarea
              id="architect-human-message"
              aria-label={`Mensagem humana — ${CONTROL_LABEL}`}
              value={decision}
              onChange={(event) => {
                setDecision(event.target.value);
                if (error !== null) {
                  setError(null);
                }
              }}
              rows={2}
              disabled={busyAction !== null}
              placeholder="Registre uma mensagem somente para este teste local"
            />
            <button
              type="submit"
              disabled={busyAction !== null}
            >
              Registrar mensagem humana — {CONTROL_LABEL}
            </button>
          </form>
        ) : null}
        {ownership === 'HUMAN' && availability.returnToUrba ? (
          <button
            type="button"
            disabled={busyAction !== null}
            onClick={() => void run('RETURN_TO_URBA')}
          >
            Devolver responsabilidade — {CONTROL_LABEL}
          </button>
        ) : null}
      </div>
      {error !== null ? <p className="architect-controls__error" role="alert">{error}</p> : null}
    </aside>
  );
}
