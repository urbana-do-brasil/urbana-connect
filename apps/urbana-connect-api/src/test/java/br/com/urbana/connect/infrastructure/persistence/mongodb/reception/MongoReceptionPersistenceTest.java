package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MongoReceptionPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void rotatesSessionLinkKeepingReplacementLineageAndOneActiveLink() {
        SpringDataAgentSessionLinkRepository repository = mock(SpringDataAgentSessionLinkRepository.class);
        AgentSessionLink current = AgentSessionLink.active("contact-1", "session-old", NOW);
        AgentSessionLink replacement = AgentSessionLink.active("contact-1", "session-new", NOW.plusSeconds(1));
        when(repository.findById("contact-1")).thenReturn(Optional.of(document(current)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentSessionLink result = new MongoAgentSessionLinkGateway(repository)
                .replaceActive("contact-1", "session-old", replacement,
                        br.com.urbana.connect.domain.reception.model.SessionLinkStatus.REPLACED);

        assertThat(result.status()).isEqualTo(br.com.urbana.connect.domain.reception.model.SessionLinkStatus.ACTIVE);
        ArgumentCaptor<AgentSessionLinkDocument> saved = ArgumentCaptor.forClass(AgentSessionLinkDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getContactId()).isEqualTo("contact-1");
        assertThat(saved.getValue().getHermesSessionId()).isEqualTo("session-new");
        assertThat(saved.getValue().getStatus())
                .isEqualTo(br.com.urbana.connect.domain.reception.model.SessionLinkStatus.ACTIVE);
        assertThat(saved.getValue().getLineage()).singleElement()
                .satisfies(lineage -> {
                    assertThat(lineage.getHermesSessionId()).isEqualTo("session-old");
                    assertThat(lineage.getReplacedBySessionId()).isEqualTo("session-new");
                });
    }

    @Test
    void productionRotationUsesOneConditionalFindAndModifyWithoutStaleSave() {
        SpringDataAgentSessionLinkRepository repository = mock(SpringDataAgentSessionLinkRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        AgentSessionLink current = AgentSessionLink.active("contact-1", "session-old", NOW);
        AgentSessionLink replacement = AgentSessionLink.active("contact-1", "session-new", NOW.plusSeconds(1));
        AgentSessionLinkDocument currentDocument = document(current);
        AgentSessionLinkDocument updated = document(replacement);
        updated.setLineage(java.util.List.of(new AgentSessionLinkDocument.SessionLineageDocument(
                "session-old", br.com.urbana.connect.domain.reception.model.SessionLinkStatus.REPLACED,
                NOW, NOW, "session-new", 1)));
        when(repository.findById("contact-1")).thenReturn(Optional.of(currentDocument));
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(AgentSessionLinkDocument.class))).thenReturn(updated);

        AgentSessionLink result = new MongoAgentSessionLinkGateway(repository, template)
                .replaceActive("contact-1", "session-old", replacement,
                        br.com.urbana.connect.domain.reception.model.SessionLinkStatus.REPLACED);

        assertThat(result.hermesSessionId()).isEqualTo("session-new");
        verify(template).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(AgentSessionLinkDocument.class));
        verify(repository, never()).save(any());
    }

    @Test
    void productionTouchCannotOverwriteAConcurrentSessionRotation() {
        SpringDataAgentSessionLinkRepository repository = mock(SpringDataAgentSessionLinkRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(AgentSessionLinkDocument.class))).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new MongoAgentSessionLinkGateway(repository, template)
                        .touchActive("contact-1", "stale-session", NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed concurrently");

        verify(template).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(AgentSessionLinkDocument.class));
        verify(repository, never()).save(any());
    }

    @Test
    void transcriptAppendIsIdempotentAndTreatsUniqueRaceAsDuplicate() {
        SpringDataReceptionMessageRepository repository = mock(SpringDataReceptionMessageRepository.class);
        MongoReceptionTranscriptGateway gateway = new MongoReceptionTranscriptGateway(repository);
        ReceptionMessage message = new ReceptionMessage("message-1", "event-1", "corr-1", "conversation-1",
                "contact-1", ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT,
                ReceptionMessageType.TEXT, "oi", null, "provider-1", NOW);
        when(repository.findByEventId("event-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(gateway.appendIfAbsent(message)).isTrue();
        when(repository.findByEventId("event-1")).thenReturn(Optional.of(new ReceptionMessageDocument()));
        assertThat(gateway.appendIfAbsent(message)).isFalse();

        when(repository.findByEventId("event-2")).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DuplicateKeyException("event-2"));
        ReceptionMessage raced = new ReceptionMessage("message-2", "event-2", "corr-1", "conversation-1",
                "contact-1", ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT,
                ReceptionMessageType.TEXT, "oi", null, "provider-2", NOW);
        assertThat(gateway.appendIfAbsent(raced)).isFalse();
    }

    @Test
    void conversationTransitionUsesExpectedVersionCasInsteadOfStaleSave() {
        SpringDataReceptionConversationRepository repository = mock(SpringDataReceptionConversationRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        ReceptionConversation current = ReceptionConversation.start("conversation-1", NOW);
        ReceptionConversation next = current.selectService("DECOR", NOW.plusSeconds(1));
        ReceptionConversationDocument updated = conversationDocument(next);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ReceptionConversationDocument.class))).thenReturn(updated);

        ReceptionConversation result = new MongoReceptionConversationGateway(repository, template).save(next);

        assertThat(result.version()).isEqualTo(1);
        verify(template).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ReceptionConversationDocument.class));
        verify(repository, never()).save(any());
    }

    @Test
    void conversationCasRejectsAStaleExpectedVersion() {
        SpringDataReceptionConversationRepository repository = mock(SpringDataReceptionConversationRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        ReceptionConversation current = ReceptionConversation.start("conversation-1", NOW);
        ReceptionConversation next = current.selectService("DECOR", NOW.plusSeconds(1));
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ReceptionConversationDocument.class))).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new MongoReceptionConversationGateway(repository, template).save(next))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("concurrently");
        verify(repository, never()).save(any());
    }

    private static AgentSessionLinkDocument document(AgentSessionLink value) {
        AgentSessionLinkDocument document = new AgentSessionLinkDocument();
        document.setContactId(value.contactId());
        document.setHermesSessionId(value.hermesSessionId());
        document.setStatus(value.status());
        document.setCreatedAt(value.createdAt());
        document.setLastUsedAt(value.lastUsedAt());
        document.setVersion(value.version());
        return document;
    }

    private static ReceptionConversationDocument conversationDocument(ReceptionConversation value) {
        ReceptionConversationDocument document = new ReceptionConversationDocument();
        document.setId(value.id());
        document.setContactId(value.contactId());
        document.setMode(value.mode());
        document.setCommercialStage(value.commercialStage());
        document.setSelectedService(value.selectedService());
        document.setTermsStatus(value.termsStatus());
        document.setPaymentStatus(value.paymentStatus());
        document.setHandoffReason(value.handoffReason());
        document.setCreatedAt(value.createdAt());
        document.setUpdatedAt(value.updatedAt());
        document.setVersion(value.version());
        return document;
    }
}
