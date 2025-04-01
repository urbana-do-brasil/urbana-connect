package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.infrastructure.persistence.ConversationMongoRepository;
import br.com.urbana.connect.infrastructure.persistence.CustomerMongoRepository;
import br.com.urbana.connect.infrastructure.persistence.MessageMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço responsável pela gestão de contexto de conversas.
 * Recupera histórico de mensagens, formata para uso pela OpenAI e 
 * gerencia o armazenamento de novas mensagens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationContextService {

    private final CustomerMongoRepository customerRepository;
    private final ConversationMongoRepository conversationRepository;
    private final MessageMongoRepository messageRepository;
    
    private static final int MAX_CONVERSATION_HISTORY_PAIRS = 5; // 5 pares de mensagens (usuário/assistente)
    
    /**
     * Recupera ou cria um cliente com base no número de telefone.
     * 
     * @param phoneNumber Número de telefone do cliente
     * @return Cliente existente ou novo
     */
    public Customer getOrCreateCustomer(String phoneNumber) {
        log.debug("Buscando cliente com número de telefone: {}", phoneNumber);
        
        return customerRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> {
                    log.info("Cliente não encontrado. Criando novo cliente com número: {}", phoneNumber);
                    Customer newCustomer = Customer.builder()
                            .id(UUID.randomUUID().toString())
                            .phoneNumber(phoneNumber)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return customerRepository.save(newCustomer);
                });
    }
    
    /**
     * Recupera ou cria uma conversa ativa para um cliente.
     * 
     * @param customer Cliente para quem a conversa será recuperada/criada
     * @return Conversa ativa
     */
    public Conversation getOrCreateActiveConversation(Customer customer) {
        log.debug("Buscando conversa ativa para cliente: {}", customer.getId());
        
        // Buscar conversa ativa existente
        Optional<Conversation> activeConversation = conversationRepository
                .findByCustomerIdOrderByStartTimeDesc(customer.getId())
                .stream()
                .filter(conv -> conv.getStatus() != ConversationStatus.CLOSED)
                .findFirst();
        
        // Retornar existente ou criar nova
        return activeConversation.orElseGet(() -> {
            log.info("Conversa ativa não encontrada. Criando nova conversa para cliente: {}", customer.getId());
            Conversation newConversation = Conversation.builder()
                    .id(UUID.randomUUID().toString())
                    .customerId(customer.getId())
                    .startTime(LocalDateTime.now())
                    .status(ConversationStatus.ACTIVE)
                    .build();
            return conversationRepository.save(newConversation);
        });
    }
    
    /**
     * Recupera o histórico de mensagens de uma conversa, limitado a um número 
     * específico de pares de mensagens.
     * 
     * @param conversation Conversa da qual recuperar o histórico
     * @return Lista de mensagens ordenadas cronologicamente
     */
    public List<Message> getConversationHistory(Conversation conversation) {
        log.debug("Recuperando histórico da conversa: {}", conversation.getId());
        
        // Recuperar as últimas mensagens, ordenadas cronologicamente
        // Limitamos a recuperar apenas os últimos N pares de mensagens (usuário/assistente)
        int messageLimit = MAX_CONVERSATION_HISTORY_PAIRS * 2; // Multiplicamos por 2 para obter N pares
        
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId());
        
        // Se houver mais mensagens que o limite, pegamos apenas as mais recentes
        if (messages.size() > messageLimit) {
            messages = messages.subList(messages.size() - messageLimit, messages.size());
        }
        
        log.debug("Recuperadas {} mensagens do histórico da conversa", messages.size());
        return messages;
    }
    
    /**
     * Formata o histórico de mensagens para uso pela API da OpenAI.
     * 
     * @param messages Lista de mensagens a serem formatadas
     * @return String formatada com o histórico
     */
    public String formatConversationHistory(List<Message> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        
        StringBuilder formattedHistory = new StringBuilder();
        
        for (Message message : messages) {
            String role = message.getDirection() == MessageDirection.INBOUND ? "User" : "Assistant";
            formattedHistory.append(role)
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }
        
        return formattedHistory.toString();
    }
    
    /**
     * Salva uma mensagem recebida do usuário na conversa.
     * 
     * @param conversation Conversa à qual a mensagem pertence
     * @param content Conteúdo da mensagem
     * @param whatsappMessageId ID da mensagem no WhatsApp (opcional)
     * @return Mensagem salva
     */
    public Message saveUserMessage(Conversation conversation, String content, String whatsappMessageId) {
        log.debug("Salvando mensagem do usuário na conversa: {}", conversation.getId());
        
        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .whatsappMessageId(whatsappMessageId)
                .conversationId(conversation.getId())
                .customerId(conversation.getCustomerId())
                .content(content)
                .direction(MessageDirection.INBOUND)
                .timestamp(LocalDateTime.now())
                .build();
        
        return messageRepository.save(message);
    }
    
    /**
     * Salva uma resposta gerada pelo assistente na conversa.
     * 
     * @param conversation Conversa à qual a resposta pertence
     * @param content Conteúdo da resposta
     * @return Mensagem salva
     */
    public Message saveAssistantResponse(Conversation conversation, String content) {
        log.debug("Salvando resposta do assistente na conversa: {}", conversation.getId());
        
        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversation.getId())
                .customerId(conversation.getCustomerId())
                .content(content)
                .direction(MessageDirection.OUTBOUND)
                .timestamp(LocalDateTime.now())
                .build();
        
        return messageRepository.save(message);
    }
    
    /**
     * Verifica se uma conversa está encerrada.
     * 
     * @param conversation Conversa a ser verificada
     * @return true se a conversa estiver encerrada, false caso contrário
     */
    public boolean isEnded(Conversation conversation) {
        return conversation.getStatus() == ConversationStatus.CLOSED;
    }
    
    /**
     * Encerra uma conversa.
     * 
     * @param conversation Conversa a ser encerrada
     * @return Conversa atualizada
     */
    public Conversation endConversation(Conversation conversation) {
        log.debug("Encerrando conversa: {}", conversation.getId());
        
        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setEndTime(LocalDateTime.now());
        
        return conversationRepository.save(conversation);
    }
    
    /**
     * Atualiza o contexto da conversa com um novo tópico e entidades identificadas.
     *
     * @param conversation Conversa a ser atualizada
     * @param detectedTopic Tópico detectado
     * @param identifiedEntities Entidades identificadas
     * @return Conversa atualizada
     */
    public Conversation updateConversationContext(Conversation conversation, 
                                                String detectedTopic,
                                                String identifiedEntities) {
        log.debug("Atualizando contexto da conversa: {}", conversation.getId());
        
        conversation.getContext().setLastDetectedTopic(detectedTopic);
        
        // Limpa a lista atual de entidades e adiciona a nova se não for nula
        conversation.getContext().getIdentifiedEntities().clear();
        if (identifiedEntities != null && !identifiedEntities.isEmpty()) {
            conversation.getContext().getIdentifiedEntities().add(identifiedEntities);
        }
        
        // Atualiza a data da última atividade
        conversation.setLastActivityTime(LocalDateTime.now());
        
        return conversationRepository.save(conversation);
    }
} 