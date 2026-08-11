package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;

import java.time.Instant;
import java.util.Objects;

/**
 * Backend-owned handoff transition. There is intentionally no return-to-AI
 * operation in this POC story; that action belongs to a later operator flow.
 */
public final class HumanHandoffService {
    private final ReceptionConversationGateway conversations;

    public HumanHandoffService(ReceptionConversationGateway conversations) {
        this.conversations = Objects.requireNonNull(conversations, "conversations");
    }

    public ReceptionConversation enterHumanMode(String contactId, String reason, Instant now) {
        ReceptionConversation conversation = conversations.findByContactId(contactId)
                .orElseThrow(() -> new IllegalStateException("conversation does not exist"));
        return enterHumanMode(conversation, reason, now);
    }

    public ReceptionConversation enterHumanMode(ReceptionConversation conversation, String reason, Instant now) {
        ReceptionConversation human = conversation.requestHumanHandoff(reason, now);
        return conversations.save(human);
    }

    public boolean isHumanMode(ReceptionConversation conversation) {
        return conversation != null && conversation.isHuman();
    }

    public void assertAutomationAllowed(ReceptionConversation conversation) {
        if (isHumanMode(conversation)) {
            throw new IllegalStateException("conversation is in HUMAN mode");
        }
    }
}
