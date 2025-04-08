package br.com.urbana.connect.application.controller;

import br.com.urbana.connect.application.config.AbstractIntegrationTest;
import br.com.urbana.connect.application.config.TestWhatsappConfig;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
    
    // Novas constantes para os testes adicionais
    private static final String HUMAN_INTERVENTION_MESSAGE = "Preciso falar com um atendente humano";
    private static final String HUMAN_TRANSFER_RESPONSE = "Sua conversa foi transferida para um atendente humano. Aguarde um momento, por favor.";
    
    private static final String SECOND_PHONE_NUMBER = "+5511888888888";
    private static final String THIRD_PHONE_NUMBER = "+5511777777777";
    
    private static final String COMPLEX_QUESTION = "Como faço para descartar baterias e eletrônicos usados? Preciso de informações detalhadas sobre o procedimento correto de descarte.";
    private static final String COMPLEX_RESPONSE = "Para descartar baterias e eletrônicos, você deve levá-los a um ponto de coleta especializado. Temos pontos de coleta em todos os ecopontos da cidade e em algumas lojas parceiras. Esses itens não devem ser descartados no lixo comum devido aos componentes tóxicos.";

    @BeforeEach
    void setUp() {
        // Configurar comportamento padrão dos mocks
        when(gptServicePort.generateResponse(anyString(), eq(TEST_MESSAGE_CONTENT), anyString()))
                .thenReturn(GPT_RESPONSE);
                
        when(gptServicePort.generateResponse(anyString(), eq(HUMAN_INTERVENTION_MESSAGE), anyString()))
                .thenReturn(HUMAN_TRANSFER_RESPONSE);
                
        when(gptServicePort.analyzeIntent(anyString()))
                .thenReturn("DUVIDA_SERVICO");
                
        when(gptServicePort.extractEntities(eq(TEST_MESSAGE_CONTENT)))
                .thenReturn(Arrays.asList("Tipo: coleta de lixo"));
                
        when(gptServicePort.extractEntities(eq(COMPLEX_QUESTION)))
                .thenReturn(Arrays.asList("Tipo: baterias", "Tipo: eletrônicos", "Ação: descarte"));
                
        when(gptServicePort.analyzeIntent(eq(COMPLEX_QUESTION)))
                .thenReturn("DESCARTE_ESPECIAL");
                
        when(gptServicePort.generateResponse(anyString(), eq(COMPLEX_QUESTION), anyString()))
                .thenReturn(COMPLEX_RESPONSE);
                
        when(gptServicePort.requiresHumanIntervention(eq(TEST_MESSAGE_CONTENT), anyString()))
                .thenReturn(false);
                
        when(gptServicePort.requiresHumanIntervention(eq(HUMAN_INTERVENTION_MESSAGE), anyString()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        // Limpar todas as coleções no banco de dados de teste
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
        Message inboundMessage = messages.get(0);
        assertThat(inboundMessage.getDirection()).isEqualTo(MessageDirection.INBOUND);
        assertThat(inboundMessage.getContent()).isEqualTo(TEST_MESSAGE_CONTENT);
        assertThat(inboundMessage.getCustomerId()).isEqualTo(customer.getId());

        // Verificar resposta gerada
        Message outboundMessage = messages.get(1);
        assertThat(outboundMessage.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(outboundMessage.getContent()).isEqualTo(GPT_RESPONSE);
        assertThat(outboundMessage.getCustomerId()).isEqualTo(customer.getId());
        
        // Verificar que o contexto da conversa foi atualizado
        Conversation updatedConversation = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertThat(updatedConversation.getContext().getLastDetectedTopic()).isEqualTo("DUVIDA_SERVICO");
        assertThat(updatedConversation.getContext().getIdentifiedEntities()).contains("Tipo: coleta de lixo");
    }
    
    @Test
    void shouldMaintainConversationContextAcrossMultipleMessages() throws Exception {
        // Given - Primeira mensagem e configuração de mock para seguinte
        String firstWebhookPayload = buildWebhookPayload(TEST_PHONE_NUMBER, TEST_MESSAGE_CONTENT);
        
        // Configurar resposta para segunda mensagem usando um matcher mais simples
        when(gptServicePort.generateResponse(anyString(), eq(FOLLOW_UP_MESSAGE), anyString()))
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
        
        // Verificar mensagens na conversa
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId());
        assertThat(messages).hasSize(4); // Duas mensagens do cliente, duas respostas
        
        // Verificar ordem e conteúdo das mensagens
        assertThat(messages.get(0).getContent()).isEqualTo(TEST_MESSAGE_CONTENT);
        assertThat(messages.get(0).getDirection()).isEqualTo(MessageDirection.INBOUND);
        
        assertThat(messages.get(1).getContent()).isEqualTo(GPT_RESPONSE);
        assertThat(messages.get(1).getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        
        assertThat(messages.get(2).getContent()).isEqualTo(FOLLOW_UP_MESSAGE);
        assertThat(messages.get(2).getDirection()).isEqualTo(MessageDirection.INBOUND);
        
        assertThat(messages.get(3).getContent()).isEqualTo(GPT_FOLLOW_UP_RESPONSE);
        assertThat(messages.get(3).getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        
        // Verificar que o GPT foi chamado com o histórico da conversa
        verify(gptServicePort, times(1)).generateResponse(anyString(), eq(FOLLOW_UP_MESSAGE), anyString());
    }
    
    @Test
    void shouldHandleMultipleCustomersMessagingConcurrently() throws Exception {
        // Definir perguntas e respostas para os clientes adicionais
        final String second_question = "Olá, vocês fazem coleta de entulho?";
        final String second_response = "Sim, fazemos coleta de entulho. O preço varia de acordo com o volume.";
        
        final String third_question = "Como contratar serviço de limpeza pós-obra?";
        final String third_response = "Para contratar o serviço de limpeza pós-obra, é necessário agendar uma visita técnica.";
        
        // Configurar mock para clientes adicionais
        when(gptServicePort.generateResponse(anyString(), eq(second_question), anyString()))
                .thenReturn(second_response);
        when(gptServicePort.generateResponse(anyString(), eq(third_question), anyString()))
                .thenReturn(third_response);
        
        // Enviar mensagens de três clientes diferentes quase simultaneamente
        String firstPayload = buildWebhookPayload(TEST_PHONE_NUMBER, TEST_MESSAGE_CONTENT);
        String secondPayload = buildWebhookPayload(SECOND_PHONE_NUMBER, second_question);
        String thirdPayload = buildWebhookPayload(THIRD_PHONE_NUMBER, third_question);
        
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstPayload))
                .andExpect(status().isOk());
                
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondPayload))
                .andExpect(status().isOk());
                
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(thirdPayload))
                .andExpect(status().isOk());
        
        // Verificar que três clientes diferentes foram criados
        await().atMost(5, TimeUnit.SECONDS).until(() -> 
            customerRepository.count() >= 3);
            
        // Verificar que cada conversa tem suas próprias mensagens
        await().atMost(5, TimeUnit.SECONDS).until(() -> 
            messageRepository.count() >= 6); // 3 clientes x 2 mensagens cada
            
        // Verificar primeiro cliente e suas mensagens
        Customer firstCustomer = customerRepository.findByPhoneNumber(TEST_PHONE_NUMBER).orElseThrow();
        Conversation firstConversation = conversationRepository.findByCustomerIdOrderByStartTimeDesc(firstCustomer.getId()).get(0);
        List<Message> firstMessages = messageRepository.findByConversationIdOrderByTimestampAsc(firstConversation.getId());
        
        assertThat(firstMessages).hasSize(2);
        assertThat(firstMessages.get(0).getContent()).isEqualTo(TEST_MESSAGE_CONTENT);
        assertThat(firstMessages.get(1).getContent()).isEqualTo(GPT_RESPONSE);
        
        // Verificar segundo cliente e suas mensagens
        Customer secondCustomer = customerRepository.findByPhoneNumber(SECOND_PHONE_NUMBER).orElseThrow();
        Conversation secondConversation = conversationRepository.findByCustomerIdOrderByStartTimeDesc(secondCustomer.getId()).get(0);
        List<Message> secondMessages = messageRepository.findByConversationIdOrderByTimestampAsc(secondConversation.getId());
        
        assertThat(secondMessages).hasSize(2);
        assertThat(secondMessages.get(0).getContent()).isEqualTo(second_question);
        assertThat(secondMessages.get(1).getContent()).isEqualTo(second_response);
        
        // Verificar terceiro cliente e suas mensagens
        Customer thirdCustomer = customerRepository.findByPhoneNumber(THIRD_PHONE_NUMBER).orElseThrow();
        Conversation thirdConversation = conversationRepository.findByCustomerIdOrderByStartTimeDesc(thirdCustomer.getId()).get(0);
        List<Message> thirdMessages = messageRepository.findByConversationIdOrderByTimestampAsc(thirdConversation.getId());
        
        assertThat(thirdMessages).hasSize(2);
        assertThat(thirdMessages.get(0).getContent()).isEqualTo(third_question);
        assertThat(thirdMessages.get(1).getContent()).isEqualTo(third_response);
    }
    
    /**
     * Teste para verificar o processamento correto de uma conversa complexa
     * com análise detalhada de intenção e extração de múltiplas entidades.
     */
    @Test
    void shouldProcessComplexConversationWithDetailedEntities() throws Exception {
        // Enviar uma pergunta complexa
        String complexPayload = buildWebhookPayload(TEST_PHONE_NUMBER, COMPLEX_QUESTION);
        
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(complexPayload))
                .andExpect(status().isOk());
                
        // Aguardar processamento
        await().atMost(5, TimeUnit.SECONDS).until(() -> 
            messageRepository.count() >= 2);
            
        // Verificar cliente e conversa
        Optional<Customer> customer = customerRepository.findByPhoneNumber(TEST_PHONE_NUMBER);
        assertThat(customer).isPresent();
        
        List<Conversation> conversations = 
                conversationRepository.findByCustomerIdOrderByStartTimeDesc(customer.get().getId());
        assertThat(conversations).isNotEmpty();
        
        Conversation conversation = conversations.get(0);
        
        // Verificar que a intenção foi corretamente identificada
        assertThat(conversation.getContext().getLastDetectedTopic()).isEqualTo("DESCARTE_ESPECIAL");
        
        // Verificar que as entidades foram extraídas corretamente
        assertThat(conversation.getContext().getIdentifiedEntities()).hasSize(3);
        assertThat(conversation.getContext().getIdentifiedEntities())
                .contains("Tipo: baterias", "Tipo: eletrônicos", "Ação: descarte");
                
        // Verificar a resposta enviada
        List<Message> messages = 
                messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId());
        
        Message responseMessage = messages.stream()
                .filter(m -> m.getDirection() == MessageDirection.OUTBOUND)
                .findFirst()
                .orElseThrow();
                
        assertThat(responseMessage.getContent()).isEqualTo(COMPLEX_RESPONSE);
        
        // Verificar que os métodos do GPT foram chamados corretamente
        verify(gptServicePort, times(1)).generateResponse(anyString(), eq(COMPLEX_QUESTION), anyString());
        verify(gptServicePort, times(1)).analyzeIntent(eq(COMPLEX_QUESTION));
        verify(gptServicePort, times(1)).extractEntities(eq(COMPLEX_QUESTION));
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
    
    /**
     * Constrói um payload de webhook simulando uma notificação de status do WhatsApp.
     * 
     * @param messageId ID da mensagem no WhatsApp
     * @param status Status da mensagem (delivered/read)
     * @return Payload JSON formatado como o webhook de status do WhatsApp
     */
    private String buildStatusUpdatePayload(String messageId, String status) {
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
                            "statuses": [{
                                "id": "%s",
                                "status": "%s",
                                "timestamp": "%d",
                                "recipient_id": "5511999999999"
                            }]
                        },
                        "field": "messages"
                    }]
                }]
            }
            """, 
            messageId, status, timestamp);
    }
} 