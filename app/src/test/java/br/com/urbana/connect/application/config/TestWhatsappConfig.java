package br.com.urbana.connect.application.config;

import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuração específica para os testes do serviço WhatsApp.
 */
@TestConfiguration
@Profile("test")
public class TestWhatsappConfig {
    
    @Bean
    @Primary
    public WhatsappServicePort testWhatsappService(ObjectMapper objectMapper) {
        return new TestWhatsappService("test_token", objectMapper);
    }
} 