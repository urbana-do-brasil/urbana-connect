package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import lombok.Data;

import java.util.Map;

@Data
public class ConversationContextDocument {
    private String paymentMethod;
    private Map<ConversationSlotName, ConversationSlotValueDocument> slots;
}
