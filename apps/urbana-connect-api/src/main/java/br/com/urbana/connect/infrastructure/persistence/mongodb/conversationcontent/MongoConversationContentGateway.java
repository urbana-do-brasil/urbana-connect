package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;

import java.util.Optional;

public class MongoConversationContentGateway implements ConversationContentGateway {

    private final SpringDataConversationContentRepository repository;

    public MongoConversationContentGateway(SpringDataConversationContentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> findActiveValue(ConversationContentKey key) {
        return repository.findFirstByKeyAndActiveTrue(key).map(ConversationContentDocument::getValue);
    }
}
