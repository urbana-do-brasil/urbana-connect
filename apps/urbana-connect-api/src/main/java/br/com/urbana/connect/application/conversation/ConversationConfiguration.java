package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.MongoConversationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.SpringDataConversationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent.MongoConversationContentGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent.SpringDataConversationContentRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage.MongoConversationMessageGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage.SpringDataConversationMessageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversationConfiguration {

    @Bean
    public ConversationGateway conversationGateway(SpringDataConversationRepository repository) {
        return new MongoConversationGateway(repository);
    }

    @Bean
    public ConversationMessageGateway conversationMessageGateway(SpringDataConversationMessageRepository repository) {
        return new MongoConversationMessageGateway(repository);
    }

    @Bean
    public ConversationContentGateway conversationContentGateway(SpringDataConversationContentRepository repository) {
        return new MongoConversationContentGateway(repository);
    }
}
