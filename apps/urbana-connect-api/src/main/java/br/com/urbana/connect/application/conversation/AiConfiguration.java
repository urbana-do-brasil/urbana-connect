package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.infrastructure.ai.gemini.GeminiAiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfiguration {

    @Bean("geminiRestClient")
    public RestClient geminiRestClient(
            RestClient.Builder builder,
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    public AiGateway aiGateway(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            ObjectMapper objectMapper,
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-2.5-flash-lite}") String model) {
        return new GeminiAiGateway(geminiRestClient, objectMapper, apiKey, model);
    }
}
