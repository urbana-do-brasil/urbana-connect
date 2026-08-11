package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataConversationContentRepository extends MongoRepository<ConversationContentDocument, String> {

    Optional<ConversationContentDocument> findFirstByKeyAndActiveTrue(ConversationContentKey key);

    Optional<ConversationContentDocument> findByKey(ConversationContentKey key);
}
