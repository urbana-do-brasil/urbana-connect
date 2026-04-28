package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.infrastructure.whatsapp.WhatsAppCloudApiGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WhatsAppConfiguration {

    @Bean("whatsAppRestClient")
    public RestClient whatsAppRestClient(
            RestClient.Builder builder,
            @Value("${whatsapp.api.base-url:https://graph.facebook.com}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    public WhatsAppMessageGateway whatsAppMessageGateway(
            @Qualifier("whatsAppRestClient") RestClient whatsAppRestClient,
            ConversationGateway conversationGateway,
            ConversationMessageGateway conversationMessageGateway,
            ConversationContentGateway conversationContentGateway,
            @Value("${whatsapp.api.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.api.access-token:}") String accessToken) {
        return new WhatsAppCloudApiGateway(
            whatsAppRestClient,
            phoneNumberId,
            accessToken,
            conversationGateway,
            conversationMessageGateway,
            conversationContentGateway
        );
    }
}
