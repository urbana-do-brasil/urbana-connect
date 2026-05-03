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
            @Value("${openclaw.poc.base-url:http://localhost:8787}") String baseUrl,
            @Value("${openclaw.poc.timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    public OpenClawClient openClawClient(
            @Qualifier("openClawPocRestClient") RestClient openClawPocRestClient,
            @Value("${openclaw.poc.turn-path:/conversation-turn}") String turnPath) {
        return new HttpOpenClawClient(openClawPocRestClient, turnPath);
    }
}
