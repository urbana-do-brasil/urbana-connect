package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationContext;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotValue;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoConversationGatewayTest {

    @Test
    void shouldIgnoreNullSlotValuesWhenSavingConversation() {
        SpringDataConversationRepository repository = mock(SpringDataConversationRepository.class);
        MongoConversationGateway gateway = new MongoConversationGateway(repository);
        Instant now = Instant.parse("2026-05-01T14:00:00Z");

        EnumMap<ConversationSlotName, ConversationSlotValue> slots = new EnumMap<>(ConversationSlotName.class);
        slots.put(ConversationSlotName.NEEDS_DISCOVERY_HELP, null);
        slots.put(
            ConversationSlotName.SUGGESTED_SERVICE,
            new ConversationSlotValue("DECOR", ConversationSlotLevel.TENTATIVE, ConversationSlotSource.INFERRED, 0.8)
        );
        Conversation conversation = new Conversation(
            "conv-1",
            "+5583999999999",
            br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE,
            ConversationStep.SERVICE_DISCOVERY,
            ServiceType.DECOR,
            new ConversationContext("PIX", ConversationStep.SERVICE_DISCOVERY, 2, slots),
            now,
            now,
            now.plusSeconds(3600)
        );

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation saved = gateway.save(conversation);

        ArgumentCaptor<ConversationDocument> captor = ArgumentCaptor.forClass(ConversationDocument.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        ConversationContextDocument contextDocument = captor.getValue().getContext();

        assertThat(contextDocument.getTurnsWithoutProgress()).isEqualTo(2);
        assertThat(contextDocument.getStagnationStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        assertThat(contextDocument.getSlots()).containsOnlyKeys(ConversationSlotName.SUGGESTED_SERVICE);
        assertThat(saved.context().slotValue(ConversationSlotName.SUGGESTED_SERVICE)).contains("DECOR");
    }

    @Test
    void shouldReturnEmptyContextWhenDocumentHasNoContext() {
        SpringDataConversationRepository repository = mock(SpringDataConversationRepository.class);
        MongoConversationGateway gateway = new MongoConversationGateway(repository);

        ConversationDocument document = new ConversationDocument();
        document.setId("conv-1");
        document.setPhoneNumber("+5583999999999");
        document.setStatus(br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE);
        document.setCurrentStep(ConversationStep.GREETING);
        document.setCreatedAt(Instant.parse("2026-05-01T14:00:00Z"));
        document.setUpdatedAt(Instant.parse("2026-05-01T14:00:00Z"));
        document.setExpiresAt(Instant.parse("2026-05-02T14:00:00Z"));
        document.setContext(null);

        when(repository.findFirstByPhoneNumberOrderByCreatedAtDesc("+5583999999999")).thenReturn(Optional.of(document));

        Conversation conversation = gateway.findLatestByPhoneNumber("+5583999999999").orElseThrow();

        assertThat(conversation.context()).isEqualTo(ConversationContext.empty());
    }

    @Test
    void shouldIgnoreNullSlotDocumentsWhenLoadingConversation() {
        SpringDataConversationRepository repository = mock(SpringDataConversationRepository.class);
        MongoConversationGateway gateway = new MongoConversationGateway(repository);

        ConversationSlotValueDocument slotDocument = new ConversationSlotValueDocument();
        slotDocument.setValue("DECOR");
        slotDocument.setLevel(ConversationSlotLevel.CONFIRMED);
        slotDocument.setSource(ConversationSlotSource.EXPLICIT);
        slotDocument.setConfidence(0.95);

        HashMap<ConversationSlotName, ConversationSlotValueDocument> slots = new HashMap<>();
        slots.put(ConversationSlotName.SUGGESTED_SERVICE, slotDocument);
        slots.put(ConversationSlotName.CONFIRMED_SERVICE, null);

        ConversationContextDocument contextDocument = new ConversationContextDocument();
        contextDocument.setPaymentMethod("PIX");
        contextDocument.setStagnationStep(ConversationStep.SERVICE_DISCOVERY);
        contextDocument.setTurnsWithoutProgress(1);
        contextDocument.setSlots(slots);

        ConversationDocument document = new ConversationDocument();
        document.setId("conv-2");
        document.setPhoneNumber("+5583888888888");
        document.setStatus(br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE);
        document.setCurrentStep(ConversationStep.SERVICE_DISCOVERY);
        document.setSelectedService(ServiceType.DECOR);
        document.setContext(contextDocument);
        document.setCreatedAt(Instant.parse("2026-05-01T14:00:00Z"));
        document.setUpdatedAt(Instant.parse("2026-05-01T14:00:00Z"));
        document.setExpiresAt(Instant.parse("2026-05-02T14:00:00Z"));

        when(repository.findFirstByPhoneNumberOrderByCreatedAtDesc("+5583888888888")).thenReturn(Optional.of(document));

        Conversation conversation = gateway.findLatestByPhoneNumber("+5583888888888").orElseThrow();

        assertThat(conversation.context().paymentMethod()).isEqualTo("PIX");
        assertThat(conversation.context().turnsWithoutProgress()).isEqualTo(1);
        assertThat(conversation.context().slotValue(ConversationSlotName.SUGGESTED_SERVICE)).contains("DECOR");
        assertThat(conversation.context().slot(ConversationSlotName.CONFIRMED_SERVICE)).isEmpty();
    }
}
