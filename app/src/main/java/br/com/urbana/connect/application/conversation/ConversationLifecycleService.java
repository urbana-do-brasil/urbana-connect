package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationStatus;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ConversationLifecycleService {

    private final ConversationGateway conversationGateway;

    public ConversationLifecycleService(ConversationGateway conversationGateway) {
        this.conversationGateway = conversationGateway;
    }

    public Conversation resumeOrStart(String phoneNumber, Instant now) {
        return conversationGateway.findLatestByPhoneNumber(phoneNumber)
            .map(existing -> resumeExistingOrRestart(existing, now))
            .orElseGet(() -> conversationGateway.save(Conversation.start(phoneNumber, now)));
    }

    private Conversation resumeExistingOrRestart(Conversation existing, Instant now) {
        if (existing.status() == ConversationStatus.ACTIVE && !existing.isExpiredAt(now)) {
            return existing;
        }

        if (existing.status() == ConversationStatus.ACTIVE && existing.isExpiredAt(now)) {
            conversationGateway.save(existing.expire(now));
        }

        return conversationGateway.save(Conversation.start(existing.phoneNumber(), now));
    }
}
