package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationContext;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotValue;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class MongoConversationGateway implements ConversationGateway {

    private final SpringDataConversationRepository repository;

    public MongoConversationGateway(SpringDataConversationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Conversation save(Conversation conversation) {
        ConversationDocument document = toDocument(conversation);
        ConversationDocument saved = repository.save(document);
        return toDomain(saved);
    }

    @Override
    public Optional<Conversation> findLatestByPhoneNumber(String phoneNumber) {
        return repository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
            .map(this::toDomain);
    }

    private ConversationDocument toDocument(Conversation conversation) {
        ConversationDocument document = new ConversationDocument();
        document.setId(conversation.id());
        document.setPhoneNumber(conversation.phoneNumber());
        document.setStatus(conversation.status());
        document.setCurrentStep(conversation.currentStep());
        document.setSelectedService(conversation.selectedService());
        document.setContext(toContextDocument(conversation.context()));
        document.setCreatedAt(conversation.createdAt());
        document.setUpdatedAt(conversation.updatedAt());
        document.setExpiresAt(conversation.expiresAt());
        return document;
    }

    private ConversationContextDocument toContextDocument(ConversationContext context) {
        ConversationContextDocument document = new ConversationContextDocument();
        document.setPaymentMethod(context == null ? null : context.paymentMethod());
        document.setStagnationStep(context == null ? null : context.stagnationStep());
        document.setTurnsWithoutProgress(context == null ? 0 : context.turnsWithoutProgress());
        document.setSlots(toSlotDocuments(context == null ? Map.of() : context.slots()));
        return document;
    }

    private Conversation toDomain(ConversationDocument document) {
        return new Conversation(
            document.getId(),
            document.getPhoneNumber(),
            document.getStatus(),
            document.getCurrentStep(),
            document.getSelectedService(),
            toContext(document.getContext()),
            document.getCreatedAt(),
            document.getUpdatedAt(),
            document.getExpiresAt()
        );
    }

    private ConversationContext toContext(ConversationContextDocument document) {
        if (document == null) {
            return ConversationContext.empty();
        }

        return new ConversationContext(
            document.getPaymentMethod(),
            document.getStagnationStep(),
            document.getTurnsWithoutProgress() == null ? 0 : document.getTurnsWithoutProgress(),
            toSlots(document.getSlots())
        );
    }

    private Map<ConversationSlotName, ConversationSlotValueDocument> toSlotDocuments(
            Map<ConversationSlotName, ConversationSlotValue> slots) {
        if (slots == null || slots.isEmpty()) {
            return Map.of();
        }

        EnumMap<ConversationSlotName, ConversationSlotValueDocument> documents = new EnumMap<>(ConversationSlotName.class);
        slots.forEach((slotName, slotValue) -> {
            if (slotName == null || slotValue == null) {
                return;
            }

            ConversationSlotValueDocument document = new ConversationSlotValueDocument();
            document.setValue(slotValue.value());
            document.setLevel(slotValue.level());
            document.setSource(slotValue.source());
            document.setConfidence(slotValue.confidence());
            documents.put(slotName, document);
        });
        return documents;
    }

    private Map<ConversationSlotName, ConversationSlotValue> toSlots(
            Map<ConversationSlotName, ConversationSlotValueDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }

        EnumMap<ConversationSlotName, ConversationSlotValue> slots = new EnumMap<>(ConversationSlotName.class);
        documents.forEach((slotName, document) -> {
            if (slotName == null || document == null) {
                return;
            }
            slots.put(slotName, new ConversationSlotValue(
                document.getValue(),
                document.getLevel(),
                document.getSource(),
                document.getConfidence()
            ).normalized());
        });
        return slots;
    }
}
