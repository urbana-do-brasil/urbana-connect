package br.com.urbana.connect.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para transferência de dados de conversas entre camadas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    
    /**
     * ID único da conversa
     */
    private String id;
    
    /**
     * ID do cliente associado à conversa
     */
    private String customerId;
    
    /**
     * Status atual da conversa (ACTIVE, WAITING, CLOSED)
     */
    private String status;
    
    /**
     * Lista de IDs de mensagens pertencentes a esta conversa
     */
    private List<String> messageIds;
    
    /**
     * Data e hora de criação da conversa
     */
    private LocalDateTime createdAt;
    
    /**
     * Data e hora da última atualização da conversa
     */
    private LocalDateTime updatedAt;
    
    /**
     * Data e hora de fechamento da conversa, se aplicável
     */
    private LocalDateTime closedAt;
} 