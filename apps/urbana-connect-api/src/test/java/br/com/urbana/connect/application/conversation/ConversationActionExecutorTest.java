package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotValue;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.conversation.model.StepFallbackBehavior;
import br.com.urbana.connect.domain.conversation.model.StructuredEscapeType;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationActionExecutorTest {

    @Mock
    private WhatsAppMessageGateway whatsAppMessageGateway;

    private ConversationActionExecutor actionExecutor;

    @BeforeEach
    void setUp() {
        actionExecutor = new ConversationActionExecutor(whatsAppMessageGateway);
    }

    @Test
    void shouldExecuteGreetingFallback() {
        actionExecutor.executeFallback(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            contract(StepFallbackBehavior.REPEAT_GREETING_WITH_BUTTONS, StructuredEscapeType.GREETING_HELP_BUTTONS),
            List.of(decor()),
            "icp prompt",
            "discovery prompt"
        );

        verify(whatsAppMessageGateway).sendUnknownInputFallback("+5583999999999");
        verify(whatsAppMessageGateway).sendGreeting("+5583999999999");
    }

    @Test
    void shouldExecuteServiceDiscoveryFallbackWithOptions() {
        Conversation conversation = Conversation.start("+5583999999999", Instant.now()).withContext(
            Conversation.start("+5583999999999", Instant.now()).context().withSlot(
                ConversationSlotName.NEEDS_DISCOVERY_HELP,
                new ConversationSlotValue("true", ConversationSlotLevel.CONFIRMED, ConversationSlotSource.EXPLICIT, 1.0)
            ),
            Instant.now()
        );

        actionExecutor.executeFallback(
            "+5583999999999",
            conversation,
            contract(StepFallbackBehavior.REPEAT_SERVICE_DISCOVERY_WITH_OPTIONS, StructuredEscapeType.SERVICE_DISCOVERY_OPTIONS),
            List.of(decor()),
            "icp prompt",
            "discovery prompt"
        );

        verify(whatsAppMessageGateway).sendUnknownInputFallback("+5583999999999");
        verify(whatsAppMessageGateway).sendTextMessage("+5583999999999", "discovery prompt");
        verify(whatsAppMessageGateway).sendGuidedTriageOptions("+5583999999999", List.of(decor()));
    }

    @Test
    void shouldExecuteConfirmationEscape() {
        Conversation conversation = Conversation.start("+5583999999999", Instant.now())
            .withSelectedService(ServiceType.DECOR, Instant.now());

        actionExecutor.sendStructuredEscape(
            "+5583999999999",
            conversation,
            StructuredEscapeType.CONFIRMATION_OPTIONS,
            List.of(decor())
        );

        verify(whatsAppMessageGateway).sendServicePresentation("+5583999999999", decor());
    }

    @Test
    void shouldExecuteConfirmationFallback() {
        Conversation conversation = Conversation.start("+5583999999999", Instant.now())
            .withSelectedService(ServiceType.DECOR, Instant.now());

        actionExecutor.executeFallback(
            "+5583999999999",
            conversation,
            contract(StepFallbackBehavior.REPEAT_CONFIRMATION, StructuredEscapeType.CONFIRMATION_OPTIONS),
            List.of(decor()),
            "icp prompt",
            "discovery prompt"
        );

        verify(whatsAppMessageGateway).sendUnknownInputFallback("+5583999999999");
        verify(whatsAppMessageGateway).sendServicePresentation("+5583999999999", decor());
    }

    @Test
    void shouldExecutePaymentOptionsEscape() {
        actionExecutor.sendStructuredEscape(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            StructuredEscapeType.PAYMENT_OPTIONS,
            List.of(decor())
        );

        verify(whatsAppMessageGateway).sendPaymentMethodOptions("+5583999999999");
    }

    @Test
    void shouldExecuteDirectDiscoveryStructuredEscapeWhenClientDoesNotNeedHelp() {
        Conversation conversation = Conversation.start("+5583999999999", Instant.now()).withContext(
            Conversation.start("+5583999999999", Instant.now()).context().withSlot(
                ConversationSlotName.NEEDS_DISCOVERY_HELP,
                new ConversationSlotValue("false", ConversationSlotLevel.CONFIRMED, ConversationSlotSource.EXPLICIT, 1.0)
            ),
            Instant.now()
        );

        actionExecutor.sendStructuredEscape(
            "+5583999999999",
            conversation,
            StructuredEscapeType.SERVICE_DISCOVERY_OPTIONS,
            List.of(decor())
        );

        verify(whatsAppMessageGateway).sendDirectTriageOptions("+5583999999999", List.of(decor()));
    }

    @Test
    void shouldSendIcpAdvanceEscapeText() {
        actionExecutor.sendStructuredEscape(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            StructuredEscapeType.ICP_ADVANCE_TO_DISCOVERY,
            List.of(decor())
        );

        verify(whatsAppMessageGateway).sendTextMessage(
            "+5583999999999",
            "Sem problema. Vou te ajudar a descobrir a melhor opção da Urba com base no que você precisa agora 😊"
        );
    }

    @Test
    void shouldExecuteTermsRetryEscape() {
        actionExecutor.sendStructuredEscape(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            StructuredEscapeType.TERMS_RETRY,
            List.of(decor())
        );

        verify(whatsAppMessageGateway).sendTermsOfUse("+5583999999999");
    }

    @Test
    void shouldExecuteGenericHelpEscape() {
        actionExecutor.sendStructuredEscape(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            StructuredEscapeType.GENERIC_HELP,
            List.of(decor())
        );

        verify(whatsAppMessageGateway).sendUnknownInputFallback("+5583999999999");
    }

    @Test
    void shouldExecutePaymentOptionsFallback() {
        actionExecutor.executeFallback(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            contract(StepFallbackBehavior.REPEAT_PAYMENT_OPTIONS, StructuredEscapeType.PAYMENT_OPTIONS),
            List.of(decor()),
            "icp prompt",
            "discovery prompt"
        );

        verify(whatsAppMessageGateway).sendUnknownInputFallback("+5583999999999");
        verify(whatsAppMessageGateway).sendPaymentMethodOptions("+5583999999999");
    }

    @Test
    void shouldExecuteGenericSafeFallback() {
        actionExecutor.executeFallback(
            "+5583999999999",
            Conversation.start("+5583999999999", Instant.now()),
            contract(StepFallbackBehavior.GENERIC_SAFE_FALLBACK, StructuredEscapeType.GENERIC_HELP),
            List.of(decor()),
            "icp prompt",
            "discovery prompt"
        );

        verify(whatsAppMessageGateway, times(2)).sendUnknownInputFallback("+5583999999999");
    }

    @Test
    void shouldSendReplyText() {
        actionExecutor.sendReply("+5583999999999", "Olá!");

        verify(whatsAppMessageGateway).sendTextMessage("+5583999999999", "Olá!");
    }

    private StepContract contract(StepFallbackBehavior fallbackBehavior, StructuredEscapeType structuredEscapeType) {
        return new StepContract(
            ConversationStep.GREETING,
            "goal",
            List.of(),
            List.of(),
            Set.of(),
            Set.of(),
            fallbackBehavior,
            2,
            structuredEscapeType,
            false
        );
    }

    private ServiceCatalogItem decor() {
        return new ServiceCatalogItem(
            ServiceType.DECOR,
            "Decor",
            "✨",
            "Renovar espaço interno",
            "Apresentação Decor",
            new BigDecimal("490.00"),
            "https://pay.example/decor",
            null,
            true
        );
    }
}
