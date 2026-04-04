package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.MongoConversationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.SpringDataConversationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversationConfiguration {

    @Bean
    public ConversationGateway conversationGateway(SpringDataConversationRepository repository) {
        return new MongoConversationGateway(repository);
    }
}
