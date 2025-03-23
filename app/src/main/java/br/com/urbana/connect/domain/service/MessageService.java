package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.enums.MessageType;
import br.com.urbana.connect.domain.port.input.ConversationManagementUseCase;
import br.com.urbana.connect.domain.port.input.CustomerManagementUseCase;
import br.com.urbana.connect.domain.port.input.MessageProcessingUseCase;
import br.com.urbana.connect.domain.port.output.GptServicePort;
import br.com.urbana.connect.domain.port.output.MessageRepository;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementação do caso de uso de processamento de mensagens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService implements MessageProcessingUseCase {
    
    private final MessageRepository messageRepository;
    private final CustomerManagementUseCase customerService;
    private final ConversationManagementUseCase conversationService;
    private final GptServicePort gptService;
    private final WhatsappServicePort whatsappService;
    
    private static final String SYSTEM_PROMPT = "Você é um assistente virtual da Urbana do Brasil, " +
            "uma empresa de coleta de resíduos e limpeza urbana. Seja cordial, educado " +
            "e forneça informações precisas sobre os serviços da empresa. " +
            "Se não souber a resposta ou se o cliente solicitar falar com um humano, " +
            "informe que irá transferir para um atendente.";
    
    @Override
    public Message processInboundMessage(Message inboundMessage) {
        log.debug("Processando mensagem recebida do cliente: {}", inboundMessage.getCustomerId());
        
        // Verificar se o cliente existe ou criar um novo
        Customer customer = ensureCustomerExists(inboundMessage.getCustomerId(), inboundMessage.getContent());
        
        // Buscar ou criar conversa ativa
        Conversation conversation = ensureActiveConversationExists(customer.getId());
        
        // Adicionar mensagem à conversa
        inboundMessage.setConversationId(conversation.getId());
        inboundMessage.setDirection(MessageDirection.INBOUND);
        inboundMessage.setStatus(MessageStatus.READ);
        inboundMessage.setTimestamp(LocalDateTime.now());
        
        Message savedMessage = messageRepository.save(inboundMessage);
        conversationService.addMessageToConversation(conversation.getId(), savedMessage);
        
        // Marcar como lida no WhatsApp
        if (inboundMessage.getWhatsappMessageId() != null) {
            whatsappService.markMessageAsRead(inboundMessage.getWhatsappMessageId());
        }
        
        // Gerar resposta
        Message response = generateResponse(conversation.getId(), savedMessage.getId());
        
        return response;
    }
    
    @Override
    @Cacheable(value = "gpt-responses", key = "#messageId")
    public Message generateResponse(String conversationId, String messageId) {
        log.debug("Gerando resposta para a mensagem: {}", messageId);
        
        // Buscar mensagem original
        Message originalMessage = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada"));
        
        // Buscar conversa
        Conversation conversation = conversationService.findConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        // Buscar histórico de mensagens
        List<Message> conversationHistory = conversationService.getConversationMessages(conversationId);
        
        // Verificar se requer intervenção humana
        boolean needsHuman = checkNeedsHumanIntervention(originalMessage, conversation, conversationHistory);
        
        if (needsHuman && !conversation.isHandedOffToHuman()) {
            return createHumanTransferMessage(conversation, originalMessage.getCustomerId());
        }
        
        // Se já foi transferido para humano, não gerar resposta automática
        if (conversation.isHandedOffToHuman()) {
            log.info("Conversa já transferida para atendimento humano. Não gerando resposta automática.");
            return null;
        }
        
        // Preparar o histórico para o GPT
        List<String> historyForGpt = prepareHistoryForGpt(conversationHistory);
        
        // Gerar resposta com GPT
        String responseContent = gptService.generateResponse(
                historyForGpt, 
                originalMessage.getContent(), 
                SYSTEM_PROMPT);
        
        // Criar e salvar a mensagem de resposta
        Message responseMessage = Message.builder()
                .conversationId(conversationId)
                .customerId(originalMessage.getCustomerId())
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content(responseContent)
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedResponse = messageRepository.save(responseMessage);
        
        // Adicionar à conversa
        conversationService.addMessageToConversation(conversationId, savedResponse);
        
        // Enviar pelo WhatsApp
        String whatsappMessageId = sendResponseViaWhatsapp(savedResponse, originalMessage.getCustomerId());
        if (whatsappMessageId != null) {
            savedResponse.setWhatsappMessageId(whatsappMessageId);
            messageRepository.save(savedResponse);
        }
        
        return savedResponse;
    }
    
    @Override
    public boolean transferToHuman(String conversationId, String reason) {
        log.debug("Transferindo conversa para atendimento humano: {}", conversationId);
        
        Conversation conversation = conversationService.findConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        if (conversation.isHandedOffToHuman()) {
            log.info("Conversa já transferida para atendimento humano: {}", conversationId);
            return true;
        }
        
        // Atualizar status da conversa
        conversation.setHandedOffToHuman(true);
        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversationService.updateConversationStatus(conversationId, ConversationStatus.WAITING_FOR_AGENT);
        
        // Adicionar mensagem de notificação
        Customer customer = customerService.findCustomerByPhoneNumber(conversation.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        Message transferMessage = Message.builder()
                .conversationId(conversationId)
                .customerId(customer.getId())
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content("Sua conversa foi transferida para um atendente humano. " +
                        "Em breve alguém entrará em contato. Motivo: " + reason)
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedMessage = messageRepository.save(transferMessage);
        conversationService.addMessageToConversation(conversationId, savedMessage);
        
        // Enviar pelo WhatsApp
        String whatsappMessageId = whatsappService.sendTextMessage(
                customer.getPhoneNumber(), transferMessage.getContent());
        
        if (whatsappMessageId != null) {
            savedMessage.setWhatsappMessageId(whatsappMessageId);
            messageRepository.save(savedMessage);
        }
        
        log.info("Conversa transferida com sucesso para atendimento humano: {}", conversationId);
        return true;
    }
    
    @Override
    public boolean processMessageStatusUpdate(String messageId, MessageStatus status) {
        log.debug("Atualizando status da mensagem: {} para {}", messageId, status);
        
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada"));
        
        message.setStatus(status);
        messageRepository.save(message);
        
        log.info("Status da mensagem atualizado com sucesso: {}", messageId);
        return true;
    }
    
    // Métodos auxiliares
    
    private Customer ensureCustomerExists(String phoneNumber, String initialMessage) {
        return customerService.findCustomerByPhoneNumber(phoneNumber)
                .orElseGet(() -> {
                    log.info("Novo cliente detectado. Criando registro para: {}", phoneNumber);
                    Customer newCustomer = Customer.builder()
                            .phoneNumber(phoneNumber)
                            .optedIn(true)
                            .lastInteraction(LocalDateTime.now())
                            .build();
                    return customerService.registerCustomer(newCustomer);
                });
    }
    
    private Conversation ensureActiveConversationExists(String customerId) {
        return conversationService.findActiveConversation(customerId)
                .orElseGet(() -> conversationService.createConversation(customerId));
    }
    
    private boolean checkNeedsHumanIntervention(Message message, Conversation conversation, List<Message> history) {
        // Verificar contexto da conversa
        if (conversation.getContext().isNeedsHumanIntervention()) {
            return true;
        }
        
        // Verificar mensagem atual
        boolean currentMessageNeedsHuman = gptService.requiresHumanIntervention(
                message.getContent(), 
                conversation.getContext().getGptContext());
        
        if (currentMessageNeedsHuman) {
            conversation.getContext().setNeedsHumanIntervention(true);
            return true;
        }
        
        return false;
    }
    
    private Message createHumanTransferMessage(Conversation conversation, String customerId) {
        Message transferMessage = Message.builder()
                .conversationId(conversation.getId())
                .customerId(customerId)
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content("Entendi que você precisa falar com um atendente humano. " +
                        "Estou transferindo sua conversa para um de nossos atendentes. " +
                        "Por favor, aguarde um momento.")
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedMessage = messageRepository.save(transferMessage);
        
        // Transferir para humano
        transferToHuman(conversation.getId(), "Solicitação do cliente ou tema complexo");
        
        return savedMessage;
    }
    
    private List<String> prepareHistoryForGpt(List<Message> conversationHistory) {
        // Limitar histórico para as últimas 10 mensagens para economizar tokens
        List<Message> limitedHistory = conversationHistory.size() <= 10 
                ? conversationHistory 
                : conversationHistory.subList(conversationHistory.size() - 10, conversationHistory.size());
        
        return limitedHistory.stream()
                .map(msg -> {
                    String prefix = msg.getDirection() == MessageDirection.INBOUND ? "Cliente: " : "Assistente: ";
                    return prefix + msg.getContent();
                })
                .collect(Collectors.toList());
    }
    
    private String sendResponseViaWhatsapp(Message response, String customerId) {
        try {
            Customer customer = customerService.findCustomerByPhoneNumber(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
            
            return whatsappService.sendTextMessage(customer.getPhoneNumber(), response.getContent());
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem via WhatsApp: {}", e.getMessage(), e);
            return null;
        }
    }
} 