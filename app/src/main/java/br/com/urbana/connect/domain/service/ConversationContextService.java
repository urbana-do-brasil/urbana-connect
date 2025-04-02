package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.application.config.ContextConfig;
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
    private final ContextConfig contextConfig;
    
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
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Inicializa o contexto da conversa
            newConversation.getContext().setLastInteractionTime(LocalDateTime.now());
            newConversation.getContext().setConversationState("INICIADA");
            
            return conversationRepository.save(newConversation);
        });
    }
    
    /**
     * Recupera o histórico de mensagens de uma conversa, limitado pelo número
     * configurado de mensagens máximas.
     * 
     * @param conversation Conversa da qual recuperar o histórico
     * @return Lista de mensagens ordenadas cronologicamente
     */
    public List<Message> getConversationHistory(Conversation conversation) {
        log.debug("Recuperando histórico da conversa: {}", conversation.getId());
        
        // Recuperar as últimas mensagens, ordenadas cronologicamente
        // Limitamos a recuperar apenas as últimas N mensagens conforme configuração
        int messageLimit = contextConfig.getMaxMessages();
        log.debug("Limite de mensagens configurado: {}", messageLimit);
        
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId());
        
        // Se houver mais mensagens que o limite, pegamos apenas as mais recentes
        if (messages.size() > messageLimit) {
            messages = messages.subList(messages.size() - messageLimit, messages.size());
        }
        
        log.debug("Recuperadas {} mensagens do histórico da conversa", messages.size());
        return messages;
    }
    
    /**
     * Estima o número de tokens em uma string.
     * Esta é uma estimativa simplificada, onde aproximadamente 4 caracteres = 1 token.
     * 
     * @param text Texto para estimar tokens
     * @return Número estimado de tokens
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Estimativa simplificada: aprox. 4 caracteres = 1 token
        return Math.max(1, text.length() / 4);
    }
    
    /**
     * Formata o histórico de mensagens para uso pela API da OpenAI.
     * Gerencia o tamanho da janela de contexto conforme as configurações.
     * 
     * @param messages Lista de mensagens a serem formatadas
     * @return String formatada com o histórico
     */
    public String formatConversationHistory(List<Message> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        
        StringBuilder formattedHistory = new StringBuilder();
        int tokenCount = 0;
        int tokenLimit = contextConfig.getTokenLimit();
        
        // Formatar primeiro da mais antiga para a mais recente
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            String role = message.getDirection() == MessageDirection.INBOUND ? "[USUARIO]" : "[ASSISTENTE]";
            String formattedMessage = role + ": " + message.getContent() + "\n\n";
            
            // Estimar tokens desta mensagem
            int messageTokens = estimateTokenCount(formattedMessage);
            
            // Se adicionar esta mensagem exceder o limite, pare (a menos que seja a primeira mensagem)
            if (tokenCount + messageTokens > tokenLimit && tokenCount > 0) {
                log.debug("Limite de tokens atingido: {} excederia o limite de {}", 
                         tokenCount + messageTokens, tokenLimit);
                break;
            }
            
            // Adiciona a mensagem ao histórico formatado
            formattedHistory.append(formattedMessage);
            tokenCount += messageTokens;
        }
        
        log.debug("Histórico formatado com aproximadamente {} tokens (limite: {})", 
                 tokenCount, tokenLimit);
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
        
        // Atualiza o timestamp da última interação no contexto
        conversation.getContext().setLastInteractionTime(LocalDateTime.now());
        conversation.getContext().setConversationState("AGUARDANDO_RESPOSTA");
        conversation.setLastActivityTime(LocalDateTime.now());
        conversationRepository.save(conversation);
        
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
        
        // Atualiza o timestamp da última interação no contexto
        conversation.getContext().setLastInteractionTime(LocalDateTime.now());
        conversation.getContext().setConversationState("AGUARDANDO_USUARIO");
        conversation.setLastActivityTime(LocalDateTime.now());
        conversationRepository.save(conversation);
        
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
        conversation.getContext().setConversationState("FINALIZADA");
        
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
        
        // Atualiza o tópico detectado
        conversation.getContext().setLastDetectedTopic(detectedTopic);
        
        // Atualiza as entidades identificadas
        if (identifiedEntities != null && !identifiedEntities.isEmpty()) {
            conversation.getContext().getIdentifiedEntities().add(identifiedEntities);
        }
        
        // Atualiza a data da última interação
        conversation.getContext().setLastInteractionTime(LocalDateTime.now());
        conversation.setLastActivityTime(LocalDateTime.now());
        
        return conversationRepository.save(conversation);
    }
    
    /**
     * Gera e atualiza um resumo da conversa no contexto.
     * Método preparado para quando a funcionalidade de resumo automático for habilitada.
     *
     * @param conversation Conversa a ser resumida
     * @param summary Resumo gerado (pode ser pelo GPT ou outro método)
     * @return Conversa atualizada
     */
    public Conversation updateConversationSummary(Conversation conversation, String summary) {
        if (!contextConfig.isSummaryEnabled() || summary == null || summary.trim().isEmpty()) {
            return conversation;
        }
        
        log.debug("Atualizando resumo da conversa: {}", conversation.getId());
        conversation.getContext().setConversationSummary(summary);
        return conversationRepository.save(conversation);
    }
} 