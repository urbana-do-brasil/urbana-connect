package br.com.urbana.connect.domain.model;

import br.com.urbana.connect.domain.enums.ConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma conversa entre um cliente e o sistema.
 * Contém metadados da conversa e referências às mensagens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {
    
    @Id
    private String id;
    
    @Indexed
    private String customerId;
    
    private ConversationStatus status;
    
    private LocalDateTime startTime;
    
    private LocalDateTime endTime;
    
    private LocalDateTime lastActivityTime;
    
    private String assignedAgentId;
    
    private boolean handedOffToHuman;
    
    @Builder.Default
    private List<String> messageIds = new ArrayList<>();
    
    @Builder.Default
    private ConversationContext context = new ConversationContext();
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    /**
     * Obtém a data de fechamento da conversa (alias para endTime).
     * @return Data e hora de fechamento da conversa
     */
    public LocalDateTime getClosedAt() {
        return endTime;
    }
} 