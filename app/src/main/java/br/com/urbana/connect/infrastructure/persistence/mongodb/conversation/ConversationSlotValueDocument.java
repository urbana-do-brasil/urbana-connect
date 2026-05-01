package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import lombok.Data;

@Data
public class ConversationSlotValueDocument {
    private String value;
    private ConversationSlotLevel level;
    private ConversationSlotSource source;
    private Double confidence;
}
