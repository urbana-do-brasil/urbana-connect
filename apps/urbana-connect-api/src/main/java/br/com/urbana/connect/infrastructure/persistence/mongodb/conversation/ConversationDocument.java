package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationStatus;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "conversations")
public class ConversationDocument {

    @Id
    private String id;

    @Indexed(unique = true, partialFilter = "{ 'status': 'ACTIVE' }")
    private String phoneNumber;

    private ConversationStatus status;
    private ConversationStep currentStep;
    private ServiceType selectedService;
    private ConversationContextDocument context;
    private Instant createdAt;
    private Instant updatedAt;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
