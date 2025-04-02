package br.com.urbana.connect.application.controller;

import br.com.urbana.connect.application.config.AbstractIntegrationTest;
import br.com.urbana.connect.application.config.TestWhatsappConfig;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.output.GptServicePort;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import br.com.urbana.connect.infrastructure.persistence.ConversationMongoRepository;
import br.com.urbana.connect.infrastructure.persistence.CustomerMongoRepository;
import br.com.urbana.connect.infrastructure.persistence.MessageMongoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWhatsappConfig.class)
class WhatsappWebhookControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerMongoRepository customerRepository;

    @Autowired
    private ConversationMongoRepository conversationRepository;

    @Autowired
    private MessageMongoRepository messageRepository;

    @Autowired
    private WhatsappServicePort whatsappService;

    @MockBean
    private GptServicePort gptServicePort;

    private static final String TEST_PHONE_NUMBER = "+5511999999999";
    private static final String TEST_MESSAGE_CONTENT = "Olá, gostaria de informações sobre coleta de lixo";
    private static final String GPT_RESPONSE = "Olá! Temos serviços de coleta de lixo residencial e comercial. Posso ajudar com mais informações específicas?";
    private static final String FOLLOW_UP_MESSAGE = "Sim, quanto custa para residências?";
    private static final String GPT_FOLLOW_UP_RESPONSE = "Para residências, o serviço de coleta de lixo básico custa R$50,00 por mês, com coletas semanais. Temos também planos premium a partir de R$80,00 com coletas duas vezes por semana.";

    @BeforeEach
    void setup() {
        // Configurar mock do GPT para respostas específicas
        when(gptServicePort.generateResponse(anyString(), eq(TEST_MESSAGE_CONTENT), anyString()))
                .thenReturn(GPT_RESPONSE);
                
        // Configurações adicionais de mocks
        when(gptServicePort.analyzeIntent(anyString())).thenReturn("DUVIDA_SERVICO");
        when(gptServicePort.requiresHumanIntervention(anyString(), anyString())).thenReturn(false);
        when(gptServicePort.extractEntities(anyString())).thenReturn(List.of("Tipo: coleta de lixo"));
    }

    @AfterEach
    void cleanup() {
        // Limpar todo o banco de dados de teste após cada teste
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void shouldProcessWebhookNotificationAndCreateCustomerConversationAndMessages() throws Exception {
        // Given - Payload simulando mensagem do WhatsApp
        String webhookPayload = buildWebhookPayload(TEST_PHONE_NUMBER, TEST_MESSAGE_CONTENT);

        // When - Enviar a requisição para o endpoint
        MvcResult result = mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"))
                .andReturn();

        // Then - Verificar cliente criado
        await().atMost(5, TimeUnit.SECONDS).until(() -> !customerRepository.findAll().isEmpty());
        
        List<Customer> customers = customerRepository.findAll();
        assertThat(customers).isNotEmpty();
        Customer customer = customers.get(0);
        assertThat(customer.getPhoneNumber()).isEqualTo(TEST_PHONE_NUMBER);

        // Verificar conversa criada
        await().atMost(5, TimeUnit.SECONDS).until(() -> !conversationRepository.findAll().isEmpty());
        
        List<Conversation> conversations = conversationRepository.findByCustomerIdOrderByStartTimeDesc(customer.getId());
        assertThat(conversations).isNotEmpty();
        Conversation conversation = conversations.get(0);

        // Verificar mensagens (entrada e saída)
        await().atMost(5, TimeUnit.SECONDS).until(() -> 
            messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId()).size() >= 2);
        
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId());
        assertThat(messages).hasSize(2); // Uma mensagem de entrada e uma resposta

        // Verificar mensagem de entrada
        Message inboundMessage = messages.stream()
                .filter(m -> m.getDirection() == MessageDirection.INBOUND)
                .findFirst()
                .orElseThrow();
        assertThat(inboundMessage.getContent()).isEqualTo(TEST_MESSAGE_CONTENT);
        assertThat(inboundMessage.getConversationId()).isEqualTo(conversation.getId());

        // Verificar mensagem de saída
        Message outboundMessage = messages.stream()
                .filter(m -> m.getDirection() == MessageDirection.OUTBOUND)
                .findFirst()
                .orElseThrow();
        assertThat(outboundMessage.getContent()).isEqualTo(GPT_RESPONSE);
        assertThat(outboundMessage.getConversationId()).isEqualTo(conversation.getId());

        // Verificar que o GPT foi chamado para gerar uma resposta
        verify(gptServicePort, times(1)).generateResponse(anyString(), anyString(), anyString());
        
        // Verificar que a intenção e entidades foram extraídas
        verify(gptServicePort, times(1)).analyzeIntent(anyString());
        verify(gptServicePort, times(1)).extractEntities(anyString());
    }
    
    @Test
    void shouldMaintainConversationContextAcrossMultipleMessages() throws Exception {
        // Given - Primeira mensagem e configuração de mock para seguinte
        String firstWebhookPayload = buildWebhookPayload(TEST_PHONE_NUMBER, TEST_MESSAGE_CONTENT);
        
        // Configurar mock para a segunda resposta com histórico formatado adequadamente
        when(gptServicePort.generateResponse(argThat(history -> 
                history.contains("[USUARIO]:") && history.contains("[ASSISTENTE]:")), 
                eq(FOLLOW_UP_MESSAGE), anyString()))
                .thenReturn(GPT_FOLLOW_UP_RESPONSE);
        
        // Enviar a primeira mensagem
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstWebhookPayload))
                .andExpect(status().isOk())
                .andReturn();
                
        // Esperar processamento
        await().atMost(5, TimeUnit.SECONDS).until(() -> 
            customerRepository.findByPhoneNumber(TEST_PHONE_NUMBER).isPresent());
        
        // When - Enviar a segunda mensagem (follow-up)
        String secondWebhookPayload = buildWebhookPayload(TEST_PHONE_NUMBER, FOLLOW_UP_MESSAGE);
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondWebhookPayload))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Verificar que a mesma conversa foi usada
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Customer customer = customerRepository.findByPhoneNumber(TEST_PHONE_NUMBER).orElseThrow();
            Conversation conversation = conversationRepository.findByCustomerIdOrderByStartTimeDesc(customer.getId()).get(0);
            return messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId()).size() >= 4;
        });
        
        // Obter a conversa atual
        Customer customer = customerRepository.findByPhoneNumber(TEST_PHONE_NUMBER).orElseThrow();
        Conversation conversation = conversationRepository.findByCustomerIdOrderByStartTimeDesc(customer.getId()).get(0);
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId());
        
        // Verificar que há 4 mensagens (2 do usuário, 2 do assistente)
        assertThat(messages).hasSize(4);
        
        // Verificar que a segunda resposta usa o contexto
        Message lastResponse = messages.get(3);
        assertThat(lastResponse.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(lastResponse.getContent()).isEqualTo(GPT_FOLLOW_UP_RESPONSE);
        
        // Verificar que o GPT foi chamado com contexto formatado para a segunda mensagem
        verify(gptServicePort, times(1)).generateResponse(
                argThat(history -> history.contains("[USUARIO]:") && history.contains("[ASSISTENTE]:")), 
                eq(FOLLOW_UP_MESSAGE), 
                anyString());
        
        // Verificar que o contexto da conversa foi atualizado com a intenção e entidades
        Conversation updatedConversation = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertThat(updatedConversation.getContext().getLastDetectedTopic()).isEqualTo("DUVIDA_SERVICO");
        assertThat(updatedConversation.getContext().getIdentifiedEntities()).isNotEmpty();
        assertThat(updatedConversation.getContext().getLastInteractionTime()).isNotNull();
        assertThat(updatedConversation.getContext().getConversationState()).isNotNull();
    }

    /**
     * Constrói um payload de webhook simulando uma mensagem recebida do WhatsApp.
     * 
     * @param phoneNumber Número de telefone do remetente
     * @param messageText Conteúdo da mensagem
     * @return Payload JSON formatado como o webhook do WhatsApp
     */
    private String buildWebhookPayload(String phoneNumber, String messageText) {
        long timestamp = System.currentTimeMillis() / 1000;
        
        return String.format("""
            {
                "object": "whatsapp_business_account",
                "entry": [{
                    "id": "123456789",
                    "changes": [{
                        "value": {
                            "messaging_product": "whatsapp",
                            "metadata": {
                                "display_phone_number": "5511111111111",
                                "phone_number_id": "123456789"
                            },
                            "contacts": [{
                                "profile": {
                                    "name": "Usuário Teste"
                                },
                                "wa_id": "%s"
                            }],
                            "messages": [{
                                "from": "%s",
                                "id": "wamid.123456789",
                                "timestamp": "%d",
                                "text": {
                                    "body": "%s"
                                },
                                "type": "text"
                            }]
                        },
                        "field": "messages"
                    }]
                }]
            }
            """, 
            phoneNumber, phoneNumber, timestamp, messageText);
    }
} 