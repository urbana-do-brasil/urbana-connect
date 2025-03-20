package br.com.urbana.connect.domain.model;

import br.com.urbana.connect.domain.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa um cliente no sistema.
 * Armazena informações de contato e preferências.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customers")
public class Customer {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String phoneNumber;
    
    private String name;
    
    private String email;
    
    private CustomerStatus status;
    
    private boolean optedIn;
    
    private LocalDateTime lastInteraction;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Builder.Default
    private Map<String, String> preferences = new HashMap<>();
} 