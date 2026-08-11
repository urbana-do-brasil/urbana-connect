package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "conversation_content")
public class ConversationContentDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private ConversationContentKey key;

    private String channel;
    private String scope;
    private String value;
    private boolean active;
    private Instant updatedAt;
}
