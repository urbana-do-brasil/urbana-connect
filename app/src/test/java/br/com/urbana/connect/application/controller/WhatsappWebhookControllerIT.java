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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.awaitility.Awaitility.await;

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

    @BeforeEach
    void setup() {
        // Configurar mock do GPT para sempre retornar a mesma resposta
        when(gptServicePort.generateResponse(any(), anyString(), anyString())).thenReturn(GPT_RESPONSE);
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
        verify(gptServicePort, times(1)).generateResponse(any(), anyString(), anyString());
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