package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.conversation.port.out.HumanHandoffGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.ConversationDocument;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage.ConversationMessageDocument;
import br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog.ServiceCatalogDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ConversationFlowServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("urbana-connect"));
    }

    @Autowired
    private ConversationFlowService conversationFlowService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private WhatsAppMessageGateway whatsAppMessageGateway;

    @MockitoBean
    private AiGateway aiGateway;

    @MockitoBean
    private HumanHandoffGateway humanHandoffGateway;

    @Autowired
    private ConversationMessageGateway conversationMessageGateway;

    @BeforeEach
    void setUp() {
        doReturn(AiInterpretation.unknown()).when(aiGateway).interpret(any());
        doReturn(ConversationalAiReply.fallback("test")).when(aiGateway).converse(any());
    }

    @Test
    void shouldStartConversationInGreetingAndSendGreetingMessage() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583999999999";

        var conversation = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "oi", ""),
            now
        );

        assertThat(conversation.currentStep()).isEqualTo(ConversationStep.GREETING);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway).sendGreeting(phoneNumber);
    }

    @Test
    void shouldPersistInboundMessageWhenWebhookFlowStartsConversation() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583111111111";

        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "oi", "", "", "text", "wamid-1"),
            now
        );

        assertThat(mongoTemplate.findAll(ConversationMessageDocument.class))
            .extracting(ConversationMessageDocument::getRawText)
            .contains("oi");
    }

    @Test
    void shouldIgnoreDuplicateInboundWebhookByProviderMessageId() {
        Instant now = Instant.parse("2026-04-05T09:02:00Z");
        String phoneNumber = "+5583111222333";

        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "oi", "", "", "text", "wamid-duplicate-1"),
            now
        );
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "oi", "", "", "text", "wamid-duplicate-1"),
            now.plusSeconds(5)
        );

        assertThat(mongoTemplate.findAll(ConversationMessageDocument.class))
            .filteredOn(message -> "wamid-duplicate-1".equals(message.getProviderMessageId()))
            .hasSize(1);
        verify(whatsAppMessageGateway, times(1)).sendGreeting(phoneNumber);
    }

    @Test
    void shouldPersistInboundListReplyWithInteractiveListType() {
        Instant now = Instant.parse("2026-04-05T09:03:00Z");
        String phoneNumber = "+5583111444555";

        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "DECOR", "Decor", "list_reply", "wamid-list-1"),
            now
        );

        assertThat(mongoTemplate.findAll(ConversationMessageDocument.class))
            .filteredOn(message -> "wamid-list-1".equals(message.getProviderMessageId()))
            .singleElement()
            .extracting(ConversationMessageDocument::getMessageType)
            .isEqualTo(ConversationMessageType.INTERACTIVE_LIST);
    }

    @Test
    void shouldMoveGreetingToIcpQualificationWhenCustomerRequestsHelp() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583888888888";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "YES_HELP"),
            now.plusSeconds(60)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.ICP_QUALIFICATION);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway).sendTextMessage(eq(phoneNumber), any());
    }

    @Test
    void shouldLogIncomingMessageAndTransitionWhenMovingFromGreetingToIcpQualification(CapturedOutput output) {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583881212121";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "YES_HELP"),
            now.plusSeconds(60)
        );

        assertThat(output.getOut()).contains("Mensagem recebida: phoneNumber=+5583***2121 type=interactive currentStep=GREETING");
        assertThat(output.getOut()).contains("Transição de conversa: phoneNumber=+5583***2121 from=GREETING to=ICP_QUALIFICATION reason=greeting_yes_help");
    }

    @Test
    void shouldMoveGreetingToIcpQualificationWhenAiInterpretsNeedForHelp() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583880000000";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        doReturn(new ConversationalAiReply(
            "Perfeito, eu te ajudo. Como você prefere que eu te trate?",
            ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE,
            List.of(new ConversationSlotUpdate(
                ConversationSlotName.NEEDS_DISCOVERY_HELP,
                "true",
                ConversationSlotLevel.CONFIRMED,
                0.95,
                ConversationSlotSource.EXPLICIT
            )),
            0.95,
            true,
            ConversationStep.ICP_QUALIFICATION,
            false,
            null
        )).when(aiGateway).converse(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "sim, preciso de ajuda", ""),
            now.plusSeconds(60)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.ICP_QUALIFICATION);
        verify(whatsAppMessageGateway).sendTextMessage(eq(phoneNumber), any());
    }

    @Test
    void shouldMoveGreetingToIcpQualificationWhenCustomerAlreadyKnowsDesiredService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583777777777";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "NO_HELP"),
            now.plusSeconds(60)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.ICP_QUALIFICATION);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway).sendTextMessage(eq(phoneNumber), any());
    }

    @Test
    void shouldMoveServiceDiscoveryToAwaitingConfirmationWhenScenarioIsSelected() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583666666666";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        advanceToServiceDiscovery(phoneNumber, now, true);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "DECOR"),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(updated.selectedService()).isEqualTo(br.com.urbana.connect.domain.servicecatalog.model.ServiceType.DECOR);
        verify(whatsAppMessageGateway).sendServicePresentation(
            eq(phoneNumber),
            argThat(service -> service.type() == br.com.urbana.connect.domain.servicecatalog.model.ServiceType.DECOR)
        );
    }

    @Test
    void shouldMoveServiceDiscoveryToAwaitingConfirmationWhenAiSelectsService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583660000000";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        advanceToServiceDiscovery(phoneNumber, now, false);
        doReturn(new AiInterpretation(IntentType.SERVICE_SELECTION, ServiceType.DECOR, null)).when(aiGateway).interpret(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "quero decor", ""),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(updated.selectedService()).isEqualTo(ServiceType.DECOR);
        verify(whatsAppMessageGateway).sendServicePresentation(
            eq(phoneNumber),
            argThat(service -> service.type() == ServiceType.DECOR)
        );
    }

    @Test
    void shouldRepeatServiceDiscoveryOptionsWhenSelectionIsUnknown() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583555555555";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        advanceToServiceDiscovery(phoneNumber, now, false);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "texto livre", ""),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        verify(whatsAppMessageGateway).sendDirectTriageOptions(eq(phoneNumber), anyList());
    }

    @Test
    void shouldNotifyHumanAndKeepConversationStepWhenCustomerRequestsHumanHandoff() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583550000000";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        advanceToServiceDiscovery(phoneNumber, now, false);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "humano", ""),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        verify(whatsAppMessageGateway).sendHumanHandoffAcknowledgement(phoneNumber);
        verify(humanHandoffGateway).notifyTeam(any());
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
    }

    @Test
    void shouldMoveToAwaitingTermsWhenCustomerConfirmsService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583333333333";

        advanceToAwaitingConfirmation(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "CONFIRM_SERVICE"),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        assertThat(updated.context().slotValue(ConversationSlotName.CONFIRMED_SERVICE)).contains("DECOR");
        verify(whatsAppMessageGateway).sendTermsOfUse(phoneNumber);
    }

    @Test
    void shouldMoveToAwaitingTermsWhenAiInterpretsAffirmation() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583330000000";

        advanceToAwaitingConfirmation(phoneNumber, now);
        doReturn(new AiInterpretation(IntentType.AFFIRMATION, null, null)).when(aiGateway).interpret(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "sim, é isso", ""),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        verify(whatsAppMessageGateway).sendTermsOfUse(phoneNumber);
    }

    @Test
    void shouldReturnToServiceDiscoveryWhenCustomerRejectsSuggestedService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583222222222";

        advanceToAwaitingConfirmation(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "RESELECT_SERVICE"),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        verify(whatsAppMessageGateway).sendDirectTriageOptions(eq(phoneNumber), anyList());
    }

    @Test
    void shouldMoveToAwaitingPaymentMethodWhenTermsAreAccepted() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583111111111";

        advanceToAwaitingTerms(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "sim aceito", ""),
            now.plusSeconds(240)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_PAYMENT_METHOD);
        verify(whatsAppMessageGateway).sendPaymentMethodOptions(phoneNumber);
    }

    @Test
    void shouldMoveToAwaitingPaymentMethodWhenCustomerAcceptsTermsByButton() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583111212121";

        advanceToAwaitingTerms(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "TERMS_ACCEPT"),
            now.plusSeconds(240)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_PAYMENT_METHOD);
        verify(whatsAppMessageGateway).sendPaymentMethodOptions(phoneNumber);
    }

    @Test
    void shouldKeepAwaitingTermsWhenCustomerDeclinesTermsByButton() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583111313131";

        advanceToAwaitingTerms(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "TERMS_DECLINE"),
            now.plusSeconds(240)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        verify(whatsAppMessageGateway, times(2)).sendTermsOfUse(phoneNumber);
    }

    @Test
    void shouldMoveToAwaitingPaymentMethodWhenAiInterpretsTermsAcceptance() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583110000000";

        advanceToAwaitingTerms(phoneNumber, now);
        doReturn(new AiInterpretation(IntentType.TERMS_ACCEPTANCE, null, null)).when(aiGateway).interpret(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "de acordo com os termos", ""),
            now.plusSeconds(240)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_PAYMENT_METHOD);
        verify(whatsAppMessageGateway).sendPaymentMethodOptions(phoneNumber);
    }

    @Test
    void shouldKeepAwaitingTermsWhenCustomerExplicitlyDeclinesEvenIfAiMisclassifiesAcceptance() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583110099999";

        advanceToAwaitingTerms(phoneNumber, now);
        doReturn(new AiInterpretation(IntentType.TERMS_ACCEPTANCE, null, null)).when(aiGateway).interpret(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "não aceito", ""),
            now.plusSeconds(240)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        verify(whatsAppMessageGateway, times(2)).sendTermsOfUse(phoneNumber);
    }

    @Test
    void shouldDiscardInvalidSuggestedServiceFromConversationalAiReply() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583110088888";

        advanceToServiceDiscovery(phoneNumber, now, false);
        doReturn(new ConversationalAiReply(
            "Entendi. Me conta mais um pouco para eu te orientar melhor.",
            ConversationalAiAction.CONFIRM_UNDERSTANDING,
            List.of(new ConversationSlotUpdate(
                ConversationSlotName.SUGGESTED_SERVICE,
                "SERVICO_INVENTADO",
                ConversationSlotLevel.TENTATIVE,
                0.91,
                ConversationSlotSource.INFERRED
            )),
            0.91,
            false,
            null,
            false,
            null
        )).when(aiGateway).converse(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "quero algo diferente", ""),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        assertThat(updated.context().slotValue(ConversationSlotName.SUGGESTED_SERVICE)).isEmpty();
    }

    @Test
    void shouldPersistPaymentMethodWhenCustomerChoosesPix() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583000000000";

        advanceToAwaitingPaymentMethod(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "PAYMENT_PIX"),
            now.plusSeconds(300)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.PAYMENT_LINK_SENT);
        assertThat(updated.context().paymentMethod()).isEqualTo("PIX");
        verify(whatsAppMessageGateway).sendPaymentLink(
            eq(phoneNumber),
            argThat(service -> service.type() == br.com.urbana.connect.domain.servicecatalog.model.ServiceType.DECOR)
        );
        verify(whatsAppMessageGateway).sendClosingMessage(phoneNumber);
    }

    @Test
    void shouldLogErrorAndReturnToServiceDiscoveryWhenServiceIsMissingForPayment(CapturedOutput output) {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583991111111";

        advanceToAwaitingPaymentMethod(phoneNumber, now);
        var removedService = mongoTemplate.findAndRemove(
            Query.query(Criteria.where("type").is(br.com.urbana.connect.domain.servicecatalog.model.ServiceType.DECOR)),
            ServiceCatalogDocument.class
        );

        try {
            var updated = conversationFlowService.handleIncomingMessage(
                new InboundWhatsAppMessage(phoneNumber, "", "PAYMENT_PIX"),
                now.plusSeconds(300)
            );

            assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
            verify(whatsAppMessageGateway).sendDirectTriageOptions(eq(phoneNumber), anyList());
            verify(whatsAppMessageGateway, times(0)).sendClosingMessage(phoneNumber);
            assertThat(output)
                    .contains("Servico DECOR nao encontrado para enviar link de pagamento para +5583***1111");
        } finally {
            if (removedService != null) {
                mongoTemplate.save(removedService);
            }
        }
    }

    @Test
    void shouldLogErrorAndNotThrowWhenGreetingSendFails(CapturedOutput output) {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583444444444";

        doThrow(new IllegalStateException("token invalido"))
            .when(whatsAppMessageGateway)
            .sendGreeting(phoneNumber);

        assertThatCode(() -> conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "oi", ""),
            now
        )).doesNotThrowAnyException();

        assertThat(output)
            .contains("Falha ao enviar mensagem para +5583***4444 na etapa GREETING: token invalido");
    }

    private long countByPhoneNumber(String phoneNumber) {
        return mongoTemplate.count(
            Query.query(Criteria.where("phoneNumber").is(phoneNumber)),
            ConversationDocument.class
        );
    }

    private void advanceToAwaitingConfirmation(String phoneNumber, Instant now) {
        advanceToServiceDiscovery(phoneNumber, now, false);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "DECOR"),
            now.plusSeconds(180)
        );
    }

    private void advanceToServiceDiscovery(String phoneNumber, Instant now, boolean needsHelp) {
        doReturn(
            new ConversationalAiReply(
                "Perfeito, podemos seguir.",
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE,
                List.of(
                    new ConversationSlotUpdate(
                        ConversationSlotName.PRONOUN_PREFERENCE,
                        "você",
                        ConversationSlotLevel.TENTATIVE,
                        0.9,
                        ConversationSlotSource.EXPLICIT
                    )
                ),
                0.95,
                true,
                ConversationStep.SERVICE_DISCOVERY,
                false,
                null
            ),
            ConversationalAiReply.fallback("test")
        ).when(aiGateway).converse(any());

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", needsHelp ? "YES_HELP" : "NO_HELP"),
            now.plusSeconds(60)
        );
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "pode me chamar de você", ""),
            now.plusSeconds(120)
        );
    }

    private void advanceToAwaitingTerms(String phoneNumber, Instant now) {
        advanceToAwaitingConfirmation(phoneNumber, now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "CONFIRM_SERVICE"),
            now.plusSeconds(180)
        );
    }

    private void advanceToAwaitingPaymentMethod(String phoneNumber, Instant now) {
        advanceToAwaitingTerms(phoneNumber, now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "aceito", ""),
            now.plusSeconds(240)
        );
    }
}
