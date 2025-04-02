package br.com.urbana.connect.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurações para gerenciamento de contexto das conversas.
 */
@Component
@ConfigurationProperties(prefix = "urbana.context")
@Data
public class ContextConfig {
    
    /**
     * Número máximo de mensagens a serem utilizadas no histórico da conversa.
     */
    private int maxMessages = 10;
    
    /**
     * Limite aproximado de tokens para o contexto enviado ao GPT.
     */
    private int tokenLimit = 1500;
    
    /**
     * Indica se o resumo automático da conversa está habilitado.
     */
    private boolean summaryEnabled = false;
} 