package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermsAcceptanceUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void recordsOnlyTheFirstAcceptanceAgainstAnAlreadyPresentedAudit() {
        MemoryGateway gateway = new MemoryGateway();
        TermsConsentAudit presented = presented();
        gateway.savePresentationIfAbsent(presented);
        TermsAcceptanceUseCase useCase = new TermsAcceptanceUseCase(gateway);

        TermsConsentAudit accepted = useCase.recordAcceptance("presentation-1", "event-accept-1", "message-accept-1",
                "Aceito", 4, NOW.plusSeconds(1));
        TermsConsentAudit replay = useCase.recordAcceptance("presentation-1", "event-accept-2", "message-accept-2",
                "Aceito novamente", 5, NOW.plusSeconds(2));

        assertThat(accepted.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
        assertThat(accepted.acceptanceTextExact()).isEqualTo("Aceito");
        assertThat(replay).isEqualTo(accepted);
    }

    @Test
    void failsClosedWithoutPresentationEvidence() {
        TermsAcceptanceUseCase useCase = new TermsAcceptanceUseCase(new MemoryGateway());
        assertThatThrownBy(() -> useCase.recordAcceptance("missing", "event", "message", "Aceito", 1, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnAcceptanceTimestampBeforeTheDurablePresentation() {
        MemoryGateway gateway = new MemoryGateway();
        gateway.savePresentationIfAbsent(presented());
        TermsAcceptanceUseCase useCase = new TermsAcceptanceUseCase(gateway);

        assertThatThrownBy(() -> useCase.recordAcceptance("presentation-1", "event", "message", "Aceito",
                4, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("precede presentation");
    }

    @Test
    void persistsTheConversationTransitionWhenTheProductionGatewayIsProvided() {
        MemoryGateway gateway = new MemoryGateway();
        gateway.savePresentationIfAbsent(presented());
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation current = ReceptionConversation.start("conversation-1", "contact-1", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW.plusSeconds(1))
                .selectService("DECOR_INTERIORES", NOW.plusSeconds(2))
                .presentTerms(NOW.plusSeconds(3))
                .activateTermsConsent("presentation-1", NOW.plusSeconds(4));
        conversations.value = current;

        TermsAcceptanceUseCase useCase = new TermsAcceptanceUseCase(gateway, conversations);
        ReceptionConversation accepted = useCase.recordAcceptance(current, "event-accept-1", "message-accept-1",
                "Aceito", NOW.plusSeconds(5), new CommercialPolicyService());

        assertThat(useCase.persistsConversation()).isTrue();
        assertThat(accepted.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
        assertThat(conversations.value).isEqualTo(accepted);
        assertThat(gateway.findByPresentationId("presentation-1").orElseThrow().status())
                .isEqualTo(TermsConsentStatus.ACCEPTED);
    }

    private static TermsConsentAudit presented() {
        return new TermsConsentAudit("presentation-1", "conversation-1", "contact-1", "turn-1", "unit-1",
                "sala", "message-environment", "DECOR_INTERIORES", "terms-v1", "v1", "invoke-1",
                "outbound-1", NOW, null, null, null, null, NOW, TermsConsentStatus.PRESENTED, 3, null);
    }

    private static final class MemoryGateway implements TermsConsentAuditGateway {
        private final HashMap<String, TermsConsentAudit> values = new HashMap<>();
        public Optional<TermsConsentAudit> findByPresentationId(String id) { return Optional.ofNullable(values.get(id)); }
        public Optional<TermsConsentAudit> findPresented(String conversationId, String unitId) {
            return values.values().stream().filter(value -> value.conversationId().equals(conversationId)
                    && value.contractingUnitId().equals(unitId) && value.status() == TermsConsentStatus.PRESENTED).findFirst();
        }
        public TermsConsentAudit savePresentationIfAbsent(TermsConsentAudit audit) { return values.computeIfAbsent(audit.presentationId(), ignored -> audit); }
        public TermsConsentAudit acceptIfPresented(String id, String eventId, String messageId, String text, long version, Instant at) {
            TermsConsentAudit current = findByPresentationId(id).orElseThrow(() -> new IllegalStateException("terms presentation evidence is missing"));
            TermsConsentAudit next = current.accept(messageId, eventId, text, at, version); values.put(id, next); return next;
        }
    }

    private static final class MemoryConversation implements ReceptionConversationGateway {
        private ReceptionConversation value;

        @Override
        public Optional<ReceptionConversation> findByContactId(String contactId) {
            return Optional.ofNullable(value).filter(conversation -> conversation.contactId().equals(contactId));
        }

        @Override
        public ReceptionConversation save(ReceptionConversation conversation) {
            return value = conversation;
        }
    }
}
