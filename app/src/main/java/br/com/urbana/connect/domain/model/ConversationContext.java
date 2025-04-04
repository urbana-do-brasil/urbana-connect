package br.com.urbana.connect.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Contexto da conversa para processamento pelo GPT.
 * Armazena informações relevantes para manter contexto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {
    
    private String customerIntent;
    
    private String lastDetectedTopic;
    
    @Builder.Default
    private List<String> identifiedEntities = new ArrayList<>();
    
    private boolean needsHumanIntervention;
    
    private String gptContext;
    
    /**
     * Timestamp da última interação.
     */
    private LocalDateTime lastInteractionTime;
    
    /**
     * Resumo do conteúdo da conversa.
     */
    private String conversationSummary;
    
    /**
     * Estado atual da conversa.
     */
    private String conversationState;
} 