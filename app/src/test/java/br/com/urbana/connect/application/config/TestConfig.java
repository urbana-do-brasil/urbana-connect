package br.com.urbana.connect.application.config;

import br.com.urbana.connect.domain.port.input.WebhookUseCase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

/**
 * Configuração específica para os testes.
 * Fornece mocks para componentes necessários nos testes.
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    /**
     * Cria um mock do WebhookUseCase para ser usado nos testes.
     */
    @Bean
    @Primary
    public WebhookUseCase webhookUseCase() {
        return mock(WebhookUseCase.class);
    }
} 