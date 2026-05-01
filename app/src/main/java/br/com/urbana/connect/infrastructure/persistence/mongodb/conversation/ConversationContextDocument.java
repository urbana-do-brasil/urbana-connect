package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import lombok.Data;

import java.util.Map;

@Data
public class ConversationContextDocument {
    private String paymentMethod;
    private ConversationStep stagnationStep;
    private Integer turnsWithoutProgress;
    private Map<ConversationSlotName, ConversationSlotValueDocument> slots;
}
