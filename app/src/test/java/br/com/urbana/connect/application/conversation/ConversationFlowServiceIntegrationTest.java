package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.conversation.port.out.HumanHandoffGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.ConversationDocument;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
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

    @BeforeEach
    void setUp() {
        doReturn(AiInterpretation.unknown()).when(aiGateway).interpret(any());
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
    void shouldMoveGreetingToGuidedTriageWhenCustomerRequestsHelp() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583888888888";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "YES_HELP"),
            now.plusSeconds(60)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_GUIDED);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway).sendGuidedTriageOptions(eq(phoneNumber), anyList());
    }

    @Test
    void shouldMoveGreetingToGuidedTriageWhenAiInterpretsAffirmation() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583880000000";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        doReturn(new AiInterpretation(IntentType.AFFIRMATION, null, null)).when(aiGateway).interpret(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "sim, preciso de ajuda", ""),
            now.plusSeconds(60)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_GUIDED);
        verify(whatsAppMessageGateway).sendGuidedTriageOptions(eq(phoneNumber), anyList());
    }

    @Test
    void shouldMoveGreetingToDirectTriageWhenCustomerAlreadyKnowsDesiredService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583777777777";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "NO_HELP"),
            now.plusSeconds(60)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_DIRECT);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway).sendDirectTriageOptions(eq(phoneNumber), anyList());
    }

    @Test
    void shouldMoveGuidedTriageToAwaitingConfirmationWhenScenarioIsSelected() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583666666666";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "YES_HELP"),
            now.plusSeconds(60)
        );

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "DECOR"),
            now.plusSeconds(120)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(updated.selectedService()).isEqualTo(br.com.urbana.connect.domain.servicecatalog.model.ServiceType.DECOR);
        verify(whatsAppMessageGateway).sendServicePresentation(
            eq(phoneNumber),
            argThat(service -> service.type() == br.com.urbana.connect.domain.servicecatalog.model.ServiceType.DECOR)
        );
    }

    @Test
    void shouldMoveDirectTriageToAwaitingConfirmationWhenAiSelectsService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583660000000";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "NO_HELP"),
            now.plusSeconds(60)
        );
        doReturn(new AiInterpretation(IntentType.SERVICE_SELECTION, ServiceType.DECOR, null)).when(aiGateway).interpret(any());

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "quero decor", ""),
            now.plusSeconds(120)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(updated.selectedService()).isEqualTo(ServiceType.DECOR);
        verify(whatsAppMessageGateway).sendServicePresentation(
            eq(phoneNumber),
            argThat(service -> service.type() == ServiceType.DECOR)
        );
    }

    @Test
    void shouldRepeatDirectTriageOptionsWhenSelectionIsUnknown() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583555555555";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "NO_HELP"),
            now.plusSeconds(60)
        );

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "texto livre", ""),
            now.plusSeconds(120)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_DIRECT);
        verify(whatsAppMessageGateway, times(2)).sendDirectTriageOptions(eq(phoneNumber), anyList());
    }

    @Test
    void shouldNotifyHumanAndKeepConversationStepWhenCustomerRequestsHumanHandoff() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583550000000";

        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "NO_HELP"),
            now.plusSeconds(60)
        );

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "humano", ""),
            now.plusSeconds(120)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_DIRECT);
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
    void shouldReturnToDirectTriageWhenCustomerRejectsSuggestedService() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583222222222";

        advanceToAwaitingConfirmation(phoneNumber, now);

        var updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "RESELECT_SERVICE"),
            now.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_DIRECT);
        verify(whatsAppMessageGateway, times(2)).sendDirectTriageOptions(eq(phoneNumber), anyList());
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
    void shouldLogErrorAndReturnToDirectTriageWhenServiceIsMissingForPayment(CapturedOutput output) {
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

            assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_DIRECT);
            verify(whatsAppMessageGateway, times(2)).sendDirectTriageOptions(eq(phoneNumber), anyList());
            verify(whatsAppMessageGateway, times(0)).sendClosingMessage(phoneNumber);
            assertThat(output)
                .contains("Servico DECOR nao encontrado para enviar link de pagamento para +5583991111111");
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
            .contains("Falha ao enviar mensagem para +5583444444444 na etapa GREETING: token invalido");
    }

    private long countByPhoneNumber(String phoneNumber) {
        return mongoTemplate.count(
            Query.query(Criteria.where("phoneNumber").is(phoneNumber)),
            ConversationDocument.class
        );
    }

    private void advanceToAwaitingConfirmation(String phoneNumber, Instant now) {
        conversationFlowService.handleIncomingMessage(new InboundWhatsAppMessage(phoneNumber, "oi", ""), now);
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "NO_HELP"),
            now.plusSeconds(60)
        );
        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "DECOR"),
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
