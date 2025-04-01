package br.com.urbana.connect.domain.model;

import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Representa uma mensagem trocada entre cliente e o sistema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {
    
    @Id
    private String id;
    
    private String conversationId;
    
    private String customerId;
    
    private MessageType type;
    
    private MessageDirection direction;
    
    private String content;
    
    private LocalDateTime timestamp;
    
    private MessageStatus status;
    
    private String whatsappMessageId;
    
    @Builder.Default
    private boolean read = false;
    
    private LocalDateTime readAt;
} 