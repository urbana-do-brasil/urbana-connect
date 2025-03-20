package br.com.urbana.connect.application.controller;

import br.com.urbana.connect.application.dto.ConversationDTO;
import br.com.urbana.connect.domain.port.input.ConversationManagementUseCase;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controlador para gerenciar conversas.
 * Fornece endpoints para listar, buscar e atualizar conversas.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationManagementUseCase conversationService;

    /**
     * Lista todas as conversas ou filtra por status se especificado.
     *
     * @param status Status opcional para filtrar as conversas
     * @return Lista de conversas convertidas para DTO
     */
    @GetMapping
    public ResponseEntity<List<ConversationDTO>> getAllConversations(
            @RequestParam(required = false) String status) {
        
        List<Conversation> conversations;
        
        if (status != null && !status.isEmpty()) {
            try {
                ConversationStatus conversationStatus = ConversationStatus.valueOf(status.toUpperCase());
                conversations = conversationService.findByStatus(conversationStatus);
                log.info("Buscando conversas pelo status: {}", conversationStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Status inválido fornecido: {}", status);
                return ResponseEntity.badRequest().build();
            }
        } else {
            conversations = conversationService.findAll();
            log.info("Buscando todas as conversas");
        }
        
        List<ConversationDTO> conversationDTOs = conversations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(conversationDTOs);
    }

    /**
     * Busca uma conversa específica pelo ID.
     *
     * @param id ID da conversa
     * @return A conversa encontrada ou 404 se não encontrada
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> getConversationById(@PathVariable String id) {
        log.info("Buscando conversa com ID: {}", id);
        
        Optional<Conversation> conversation = conversationService.findById(id);
        
        return conversation
                .map(c -> ResponseEntity.ok(convertToDTO(c)))
                .orElseGet(() -> {
                    log.warn("Conversa com ID {} não encontrada", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Lista todas as conversas de um cliente específico.
     *
     * @param customerId ID do cliente
     * @param status     Status opcional para filtrar as conversas
     * @return Lista de conversas do cliente
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ConversationDTO>> getConversationsByCustomerId(
            @PathVariable String customerId,
            @RequestParam(required = false) String status) {
        
        List<Conversation> conversations;
        
        if (status != null && !status.isEmpty()) {
            try {
                ConversationStatus conversationStatus = ConversationStatus.valueOf(status.toUpperCase());
                log.info("Buscando conversas do cliente {} com status {}", customerId, conversationStatus);
                conversations = conversationService.findByCustomerIdAndStatus(customerId, conversationStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Status inválido fornecido para busca de cliente {}: {}", customerId, status);
                return ResponseEntity.badRequest().build();
            }
        } else {
            log.info("Buscando todas as conversas do cliente {}", customerId);
            conversations = conversationService.findByCustomerId(customerId);
        }
        
        List<ConversationDTO> conversationDTOs = conversations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(conversationDTOs);
    }

    /**
     * Atualiza o status de uma conversa.
     *
     * @param id     ID da conversa
     * @param status Novo status
     * @return A conversa atualizada
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ConversationDTO> updateConversationStatus(
            @PathVariable String id,
            @RequestParam String status) {
        
        try {
            ConversationStatus conversationStatus = ConversationStatus.valueOf(status.toUpperCase());
            log.info("Atualizando status da conversa {} para {}", id, conversationStatus);
            
            Conversation updatedConversation = conversationService.updateConversationStatus(id, conversationStatus);
            return ResponseEntity.ok(convertToDTO(updatedConversation));
            
        } catch (IllegalArgumentException e) {
            log.warn("Status inválido fornecido para atualização: {}", status);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erro ao atualizar status da conversa {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Fecha uma conversa.
     *
     * @param id ID da conversa a ser fechada
     * @return A conversa fechada
     */
    @PatchMapping("/{id}/close")
    public ResponseEntity<ConversationDTO> closeConversation(@PathVariable String id) {
        log.info("Fechando conversa com ID: {}", id);
        
        try {
            Conversation closedConversation = conversationService.closeConversation(id);
            return ResponseEntity.ok(convertToDTO(closedConversation));
        } catch (Exception e) {
            log.error("Erro ao fechar conversa {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Converte uma entidade Conversation para DTO.
     *
     * @param conversation A entidade Conversation
     * @return O DTO correspondente
     */
    private ConversationDTO convertToDTO(Conversation conversation) {
        return ConversationDTO.builder()
                .id(conversation.getId())
                .customerId(conversation.getCustomerId())
                .status(conversation.getStatus().name())
                .messageIds(conversation.getMessageIds())
                .createdAt(conversation.getStartTime())
                .updatedAt(conversation.getLastActivityTime())
                .closedAt(conversation.getEndTime())
                .build();
    }
} 