package br.com.urbana.connect.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO para transferência de dados de clientes entre camadas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    
    /**
     * ID único do cliente
     */
    private String id;
    
    /**
     * Nome do cliente
     */
    private String name;
    
    /**
     * Número de telefone do cliente (formato WhatsApp)
     */
    private String phoneNumber;
    
    /**
     * E-mail do cliente (opcional)
     */
    private String email;
    
    /**
     * Mapa de preferências do cliente (chave-valor)
     */
    private Map<String, String> preferences;
    
    /**
     * Data e hora de criação do registro do cliente
     */
    private LocalDateTime createdAt;
    
    /**
     * Data e hora da última atualização do registro do cliente
     */
    private LocalDateTime updatedAt;
} 