package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage;

import br.com.urbana.connect.domain.conversation.model.ConversationMessageDirection;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageSenderType;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "conversation_messages")
public class ConversationMessageDocument {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    @Indexed
    private String phoneNumber;

    private String channel;
    private ConversationMessageDirection direction;
    private ConversationMessageSenderType senderType;
    private ConversationMessageType messageType;
    private String rawText;
    private String interactiveReplyId;
    @Indexed(unique = true, sparse = true)
    private String providerMessageId;
    private Instant createdAt;
    private String stepAtTime;
}
