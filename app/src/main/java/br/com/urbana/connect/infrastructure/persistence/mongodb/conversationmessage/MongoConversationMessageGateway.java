package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage;

import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import org.springframework.data.domain.PageRequest;

import java.util.Comparator;
import java.util.List;

public class MongoConversationMessageGateway implements ConversationMessageGateway {

    private final SpringDataConversationMessageRepository repository;

    public MongoConversationMessageGateway(SpringDataConversationMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public ConversationMessage save(ConversationMessage message) {
        ConversationMessageDocument document = toDocument(message);
        return toDomain(repository.save(document));
    }

    @Override
    public List<ConversationMessage> findRecentByConversationId(String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank() || limit <= 0) {
            return List.of();
        }

        return repository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit)).stream()
            .map(this::toDomain)
            .sorted(Comparator.comparing(ConversationMessage::createdAt))
            .toList();
    }

    private ConversationMessageDocument toDocument(ConversationMessage message) {
        ConversationMessageDocument document = new ConversationMessageDocument();
        document.setId(message.id());
        document.setConversationId(message.conversationId());
        document.setPhoneNumber(message.phoneNumber());
        document.setChannel(message.channel());
        document.setDirection(message.direction());
        document.setSenderType(message.senderType());
        document.setMessageType(message.messageType());
        document.setRawText(message.rawText());
        document.setInteractiveReplyId(message.interactiveReplyId());
        document.setProviderMessageId(message.providerMessageId());
        document.setCreatedAt(message.createdAt());
        document.setStepAtTime(message.stepAtTime());
        return document;
    }

    private ConversationMessage toDomain(ConversationMessageDocument document) {
        return new ConversationMessage(
            document.getId(),
            document.getConversationId(),
            document.getPhoneNumber(),
            document.getChannel(),
            document.getDirection(),
            document.getSenderType(),
            document.getMessageType(),
            document.getRawText(),
            document.getInteractiveReplyId(),
            document.getProviderMessageId(),
            document.getCreatedAt(),
            document.getStepAtTime()
        );
    }
}
