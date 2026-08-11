package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.infrastructure.hermes.HermesAgentOutputParser;

import java.util.List;

/** Reconciles untrusted agent JSON with the authoritative operational state. */
public final class AgentOutputReconciler {
    private final HermesAgentOutputParser parser;

    public AgentOutputReconciler() {
        this(new HermesAgentOutputParser());
    }

    public AgentOutputReconciler(HermesAgentOutputParser parser) {
        this.parser = parser;
    }

    public AgentOutput reconcile(String rawContent, ReceptionConversation conversation,
                                 List<DomainToolInvocation> ledger) {
        return reconcile(parser.parseOperationalEnvelope(rawContent), conversation, ledger);
    }

    public AgentOutput reconcile(AgentOutput output, ReceptionConversation conversation,
                                 List<DomainToolInvocation> ledger) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation is required");
        }
        List<DomainToolInvocation> safeLedger = ledger == null ? List.of() : ledger;
        if (conversation.isHuman() && output.nextAction() != AgentNextAction.HANDOFF) {
            throw new ReconciliationException("human mode cannot publish an AI action");
        }
        AgentOutput operational = reconcilePaymentAction(output, conversation);
        switch (operational.nextAction()) {
            case HANDOFF -> requireSuccessful(safeLedger, DomainToolName.REQUEST_HUMAN_HANDOFF,
                    "HANDOFF output has no successful handoff invocation");
            case AWAIT_PAYMENT_PROOF -> {
                if (conversation.paymentStatus() != PaymentStatus.PREPARED) {
                    throw new ReconciliationException("payment proof cannot be awaited before payment preparation");
                }
            }
            case AWAIT_PAYMENT_APPROVAL -> {
                if (conversation.paymentStatus() != PaymentStatus.PROOF_RECEIVED) {
                    throw new ReconciliationException("payment approval cannot be awaited without proof");
                }
            }
            default -> { }
        }
        return operational;
    }

    private AgentOutput reconcilePaymentAction(AgentOutput output, ReceptionConversation conversation) {
        if (output.nextAction() == AgentNextAction.HANDOFF) {
            return output;
        }
        return switch (conversation.paymentStatus()) {
            case PREPARED -> output.nextAction() == AgentNextAction.AWAIT_PAYMENT_PROOF
                    ? output : new AgentOutput(output.message(), AgentNextAction.AWAIT_PAYMENT_PROOF);
            case PROOF_RECEIVED -> output.nextAction() == AgentNextAction.AWAIT_PAYMENT_APPROVAL
                    ? output : new AgentOutput(output.message(), AgentNextAction.AWAIT_PAYMENT_APPROVAL);
            default -> output;
        };
    }

    public AgentOutput safeFallback(String reason) {
        String normalized = reason == null || reason.isBlank()
                ? "Não foi possível validar a próxima etapa." : reason;
        return new AgentOutput(normalized + " Por favor, tente novamente.", AgentNextAction.AWAIT_CUSTOMER);
    }

    private void requireSuccessful(List<DomainToolInvocation> ledger, DomainToolName name, String message) {
        boolean successful = ledger.stream().anyMatch(invocation -> invocation.toolName() == name
                && invocation.status() == DomainToolInvocationStatus.SUCCEEDED);
        if (!successful) {
            throw new ReconciliationException(message);
        }
    }

    public static final class ReconciliationException extends IllegalArgumentException {
        public ReconciliationException(String message) {
            super(message);
        }
    }
}
