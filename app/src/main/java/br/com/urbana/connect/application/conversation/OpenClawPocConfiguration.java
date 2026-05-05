package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.port.out.OpenClawClient;
import br.com.urbana.connect.infrastructure.openclaw.HttpOpenClawClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenClawPocConfiguration {

    @Bean("openClawPocRestClient")
    public RestClient openClawPocRestClient(
            RestClient.Builder builder,
            @Value("${openclaw.poc.base-url:http://localhost:18789}") String baseUrl,
            @Value("${openclaw.poc.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${openclaw.poc.timeout-ms:45000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    public OpenClawClient openClawClient(
            @Qualifier("openClawPocRestClient") RestClient openClawPocRestClient,
            @Value("${openclaw.poc.chat-completions-path:/v1/chat/completions}") String chatCompletionsPath,
            @Value("${openclaw.poc.gateway-token:}") String gatewayToken,
            @Value("${openclaw.poc.model:openclaw/urba}") String model) {
        return new HttpOpenClawClient(openClawPocRestClient, chatCompletionsPath, gatewayToken, model);
    }
}
