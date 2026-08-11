package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOutputReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void derivesPaymentProofActionFromAuthoritativePreparedState() {
        ReceptionConversation conversation = conversation(PaymentStatus.PREPARED);
        AgentOutput candidate = new AgentOutput("Envie o comprovante quando puder.", AgentNextAction.AWAIT_CUSTOMER);

        AgentOutput reconciled = new AgentOutputReconciler().reconcile(candidate, conversation, List.of());

        assertThat(reconciled.message()).isEqualTo(candidate.message());
        assertThat(reconciled.nextAction()).isEqualTo(AgentNextAction.AWAIT_PAYMENT_PROOF);
    }

    @Test
    void derivesApprovalActionFromAuthoritativeProofReceivedState() {
        ReceptionConversation conversation = conversation(PaymentStatus.PROOF_RECEIVED);
        AgentOutput candidate = new AgentOutput("Comprovante recebido.", AgentNextAction.AWAIT_CUSTOMER);

        AgentOutput reconciled = new AgentOutputReconciler().reconcile(candidate, conversation, List.of());

        assertThat(reconciled.message()).isEqualTo(candidate.message());
        assertThat(reconciled.nextAction()).isEqualTo(AgentNextAction.AWAIT_PAYMENT_APPROVAL);
    }

    private static ReceptionConversation conversation(PaymentStatus paymentStatus) {
        return new ReceptionConversation("conversation-1", "poc:ana", ReceptionMode.AI,
                CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED, paymentStatus,
                null, NOW, NOW, 1);
    }
}
