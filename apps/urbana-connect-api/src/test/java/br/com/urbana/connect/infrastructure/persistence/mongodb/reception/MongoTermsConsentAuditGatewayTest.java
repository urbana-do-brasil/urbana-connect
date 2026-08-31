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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
