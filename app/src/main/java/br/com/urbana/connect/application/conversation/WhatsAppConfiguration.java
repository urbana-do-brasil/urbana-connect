package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.infrastructure.whatsapp.WhatsAppCloudApiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WhatsAppConfiguration {

    @Bean
    public RestClient whatsAppRestClient(
            RestClient.Builder builder,
            @Value("${whatsapp.api.base-url:https://graph.facebook.com}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    public WhatsAppMessageGateway whatsAppMessageGateway(
            RestClient whatsAppRestClient,
            @Value("${whatsapp.api.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.api.access-token:}") String accessToken) {
        return new WhatsAppCloudApiGateway(whatsAppRestClient, phoneNumberId, accessToken);
    }
}
