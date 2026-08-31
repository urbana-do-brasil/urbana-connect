package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoTermsConsentAuditGatewayTest {
    private static final Instant PRESENTED_AT = Instant.parse("2026-08-28T12:00:00Z");

    @Mock
    private SpringDataTermsConsentAuditRepository repository;

    @Mock
    private MongoTemplate template;

    @Test
    void neverOverwritesAnExistingAcceptedPresentationWhenAConversationRetriesPublication() {
        TermsConsentAuditDocument existing = document(TermsConsentStatus.ACCEPTED);
        when(repository.findById("presentation-1")).thenReturn(Optional.of(existing));

        TermsConsentAudit result = gateway().savePresentationIfAbsent(presented());

        assertThat(result.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
        assertThat(result.acceptanceTextExact()).isEqualTo("Aceito");
        verify(repository, never()).insert(any(TermsConsentAuditDocument.class));
        verify(repository, never()).save(any(TermsConsentAuditDocument.class));
    }

    @Test
    void acceptanceCasRequiresPresentedStatusAndAResourceAlreadyVisibleInTime() {
        TermsConsentAuditDocument accepted = document(TermsConsentStatus.ACCEPTED);
        when(template.findAndModify(any(Query.class), any(), any(FindAndModifyOptions.class),
                eq(TermsConsentAuditDocument.class))).thenReturn(accepted);

        TermsConsentAudit result = gateway().acceptIfPresented("presentation-1", "event-1", "message-1",
                "Aceito", 4, PRESENTED_AT.plusSeconds(1));

        assertThat(result.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).findAndModify(query.capture(), any(), any(FindAndModifyOptions.class),
                eq(TermsConsentAuditDocument.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains("status=PRESENTED")
                .contains("presentedAt");
    }

    @Test
    void mapsRepositoryQueriesAndEveryAuditFieldBackToTheDomain() {
        TermsConsentAuditDocument document = document(TermsConsentStatus.ACCEPTED);
        when(repository.findById("presentation-1")).thenReturn(Optional.of(document));
        when(repository.findFirstByConversationIdAndContractingUnitIdAndStatusOrderByPresentedAtDesc(
                "conversation-1", "unit-1", TermsConsentStatus.PRESENTED)).thenReturn(Optional.of(document));

        MongoTermsConsentAuditGateway gateway = gateway();
        TermsConsentAudit byId = gateway.findByPresentationId("presentation-1").orElseThrow();
        TermsConsentAudit presented = gateway.findPresented("conversation-1", "unit-1").orElseThrow();

        assertThat(byId).isEqualTo(presented);
        assertThat(byId).satisfies(value -> {
            assertThat(value.presentationId()).isEqualTo("presentation-1");
            assertThat(value.conversationId()).isEqualTo("conversation-1");
            assertThat(value.contactId()).isEqualTo("contact-1");
            assertThat(value.turnId()).isEqualTo("turn-1");
            assertThat(value.contractingUnitId()).isEqualTo("unit-1");
            assertThat(value.environmentLabelSnapshot()).isEqualTo("sala");
            assertThat(value.environmentSourceMessageId()).isEqualTo("message-environment");
            assertThat(value.serviceType()).isEqualTo("DECOR_INTERIORES");
            assertThat(value.termsResource()).contains("terms/decor");
            assertThat(value.termsVersion()).isEqualTo("v1");
            assertThat(value.prepareTermsInvocationId()).isEqualTo("invoke-1");
            assertThat(value.termsOutboundMessageId()).isEqualTo("outbound-1");
            assertThat(value.acceptanceMessageId()).isEqualTo("message-accept-1");
            assertThat(value.acceptanceEventId()).isEqualTo("event-accept-1");
            assertThat(value.acceptanceTextExact()).isEqualTo("Aceito");
            assertThat(value.acceptedAt()).isEqualTo(PRESENTED_AT.plusSeconds(1));
            assertThat(value.recordedAt()).isEqualTo(PRESENTED_AT);
            assertThat(value.conversationVersionAtPresentation()).isEqualTo(3);
            assertThat(value.conversationVersionAtAcceptance()).isEqualTo(4L);
        });
    }

    @Test
    void insertsPresentedEvidenceAndPreservesTheMappedDocument() {
        TermsConsentAuditDocument inserted = document(TermsConsentStatus.PRESENTED);
        when(repository.findById("presentation-1")).thenReturn(Optional.empty());
        when(repository.insert(any(TermsConsentAuditDocument.class))).thenReturn(inserted);

        TermsConsentAudit result = gateway().savePresentationIfAbsent(presented());

        assertThat(result).isEqualTo(presented());
        ArgumentCaptor<TermsConsentAuditDocument> captured = ArgumentCaptor.forClass(TermsConsentAuditDocument.class);
        verify(repository).insert(captured.capture());
        assertThat(captured.getValue()).satisfies(value -> {
            assertThat(value.getPresentationId()).isEqualTo("presentation-1");
            assertThat(value.getPrepareTermsInvocationId()).isEqualTo("invoke-1");
            assertThat(value.getAcceptanceEventId()).isNull();
            assertThat(value.getStatus()).isEqualTo(TermsConsentStatus.PRESENTED);
        });
    }

    @Test
    void handlesDuplicatePresentationRacesUsingTheWinnerOrRethrowsWhenItDisappears() {
        TermsConsentAuditDocument winner = document(TermsConsentStatus.ACCEPTED);
        when(repository.findById("presentation-1")).thenReturn(Optional.empty(), Optional.of(winner));
        DuplicateKeyException duplicate = new DuplicateKeyException("duplicate presentation");
        when(repository.insert(any(TermsConsentAuditDocument.class))).thenThrow(duplicate);

        TermsConsentAudit recovered = gateway().savePresentationIfAbsent(presented());
        assertThat(recovered.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
        assertThat(recovered.acceptanceTextExact()).isEqualTo("Aceito");

        when(repository.findById("presentation-2")).thenReturn(Optional.empty());
        TermsConsentAudit second = new TermsConsentAudit("presentation-2", "conversation-1", "contact-1", "turn-1",
                "unit-1", "sala", "message-environment", "DECOR_INTERIORES",
                "https://fixtures.urbana.local/terms/decor", "v1", "invoke-2", "outbound-2", PRESENTED_AT,
                null, null, null, null, PRESENTED_AT, TermsConsentStatus.PRESENTED, 3, null);
        when(repository.insert(any(TermsConsentAuditDocument.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> gateway().savePresentationIfAbsent(second))
                .isSameAs(duplicate);
    }

    @Test
    void rejectsInvalidPresentationPayloadsAndAcceptanceBoundaryArguments() {
        TermsConsentAudit accepted = presented().accept("message-accept", "event-accept", "Aceito",
                PRESENTED_AT.plusSeconds(1), 4);
        assertThatThrownBy(() -> gateway().savePresentationIfAbsent(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway().savePresentationIfAbsent(accepted))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> gateway().acceptIfPresented(null, "event", "message", "Aceito", 0, PRESENTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("presentationId");
        assertThatThrownBy(() -> gateway().acceptIfPresented("id", "", "message", "Aceito", 0, PRESENTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptanceEventId");
        assertThatThrownBy(() -> gateway().acceptIfPresented("id", "event", " ", "Aceito", 0, PRESENTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptanceMessageId");
        assertThatThrownBy(() -> gateway().acceptIfPresented("id", "event", "message", "", 0, PRESENTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptanceTextExact");
        assertThatThrownBy(() -> gateway().acceptIfPresented("id", "event", "message", "Aceito", -1, PRESENTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-negative");
        assertThatThrownBy(() -> gateway().acceptIfPresented("id", "event", "message", "Aceito", 0, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptedAt");
    }

    @Test
    void returnsExistingAcceptedEvidenceWhenCompareAndSetFindsNoChange() {
        TermsConsentAuditDocument accepted = document(TermsConsentStatus.ACCEPTED);
        when(template.findAndModify(any(Query.class), any(), any(FindAndModifyOptions.class),
                eq(TermsConsentAuditDocument.class))).thenReturn(null);
        when(repository.findById("presentation-1")).thenReturn(Optional.of(accepted));

        assertThat(gateway().acceptIfPresented("presentation-1", "event-new", "message-new", "new text", 9,
                PRESENTED_AT.plusSeconds(5))).satisfies(result -> {
            assertThat(result.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
            assertThat(result.acceptanceEventId()).isEqualTo("event-accept-1");
            assertThat(result.acceptanceTextExact()).isEqualTo("Aceito");
        });
    }

    @Test
    void failsClosedWhenCompareAndSetCannotFindPresentationOrAcceptedWinner() {
        when(template.findAndModify(any(Query.class), any(), any(FindAndModifyOptions.class),
                eq(TermsConsentAuditDocument.class))).thenReturn(null);
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gateway().acceptIfPresented("missing", "event", "message", "Aceito", 1,
                PRESENTED_AT)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidence is missing");

        TermsConsentAuditDocument stillPresented = document(TermsConsentStatus.PRESENTED);
        when(repository.findById("presentation-1")).thenReturn(Optional.of(stillPresented));
        assertThatThrownBy(() -> gateway().acceptIfPresented("presentation-1", "event", "message", "Aceito", 1,
                PRESENTED_AT)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was not recorded");
    }

    private MongoTermsConsentAuditGateway gateway() {
        return new MongoTermsConsentAuditGateway(repository, template);
    }

    private static TermsConsentAudit presented() {
        return new TermsConsentAudit("presentation-1", "conversation-1", "contact-1", "turn-1", "unit-1",
                "sala", "message-environment", "DECOR_INTERIORES", "https://fixtures.urbana.local/terms/decor",
                "v1", "invoke-1", "outbound-1", PRESENTED_AT, null, null, null, null, PRESENTED_AT,
                TermsConsentStatus.PRESENTED, 3, null);
    }

    private static TermsConsentAuditDocument document(TermsConsentStatus status) {
        TermsConsentAuditDocument document = new TermsConsentAuditDocument();
        document.setPresentationId("presentation-1");
        document.setConversationId("conversation-1");
        document.setContactId("contact-1");
        document.setTurnId("turn-1");
        document.setContractingUnitId("unit-1");
        document.setEnvironmentLabelSnapshot("sala");
        document.setEnvironmentSourceMessageId("message-environment");
        document.setServiceType("DECOR_INTERIORES");
        document.setTermsResource("https://fixtures.urbana.local/terms/decor");
        document.setTermsVersion("v1");
        document.setPrepareTermsInvocationId("invoke-1");
        document.setTermsOutboundMessageId("outbound-1");
        document.setPresentedAt(PRESENTED_AT);
        document.setRecordedAt(PRESENTED_AT);
        document.setStatus(status);
        document.setConversationVersionAtPresentation(3);
        if (status == TermsConsentStatus.ACCEPTED) {
            document.setAcceptanceMessageId("message-accept-1");
            document.setAcceptanceEventId("event-accept-1");
            document.setAcceptanceTextExact("Aceito");
            document.setAcceptedAt(PRESENTED_AT.plusSeconds(1));
            document.setConversationVersionAtAcceptance(4L);
        }
        return document;
    }
}
