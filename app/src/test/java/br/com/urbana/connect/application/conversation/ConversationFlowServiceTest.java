package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.HumanHandoffGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationFlowServiceTest {

    @Mock
    private ConversationLifecycleService conversationLifecycleService;

    @Mock
    private ConversationGateway conversationGateway;

    @Mock
    private ServiceCatalogGateway serviceCatalogGateway;

    @Mock
    private ConversationMessageGateway conversationMessageGateway;

    @Mock
    private WhatsAppMessageGateway whatsAppMessageGateway;

    @Mock
    private AiGateway aiGateway;

    @Mock
    private HumanHandoffGateway humanHandoffGateway;

    @InjectMocks
    private ConversationFlowService conversationFlowService;

    @BeforeEach
    void setUp() {
        lenient().when(serviceCatalogGateway.findAvailable()).thenReturn(List.of(decor()));
        lenient().when(conversationGateway.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(conversationMessageGateway.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(conversationMessageGateway.findRecentByConversationId(any(), anyInt())).thenReturn(List.of());
        lenient().when(conversationMessageGateway.existsByProviderMessageId(any())).thenReturn(false);
        lenient().when(aiGateway.interpret(any())).thenReturn(AiInterpretation.unknown());
        lenient().when(aiGateway.converse(any())).thenReturn(ConversationalAiReply.fallback("test"));
    }

    @Test
    void shouldMoveGreetingToIcpQualificationWhenAiConfirmsNeedForHelp() {
        Instant now = Instant.parse("2026-04-06T10:00:00Z");
        String phoneNumber = "+5583999999999";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        when(aiGateway.converse(any())).thenReturn(new ConversationalAiReply(
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
        ));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "sim, preciso de ajuda", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.ICP_QUALIFICATION);
        verify(whatsAppMessageGateway).sendTextMessage(eq(phoneNumber), any());
    }

    @Test
    void shouldMoveDirectTriageToAwaitingConfirmationWhenAiSelectsService() {
        Instant now = Instant.parse("2026-04-06T10:05:00Z");
        String phoneNumber = "+5583888888888";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(120))
            .moveTo(ConversationStep.TRIAGE_DIRECT, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        when(aiGateway.interpret(any())).thenReturn(new AiInterpretation(IntentType.SERVICE_SELECTION, ServiceType.DECOR, null));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "quero decor", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(updated.selectedService()).isEqualTo(ServiceType.DECOR);
        verify(whatsAppMessageGateway).sendServicePresentation(eq(phoneNumber), any(ServiceCatalogItem.class));
    }

    @Test
    void shouldMoveAwaitingTermsToPaymentMethodWhenAiAcceptsTerms() {
        Instant now = Instant.parse("2026-04-06T10:10:00Z");
        String phoneNumber = "+5583777777777";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(180))
            .selectService(ServiceType.DECOR, ConversationStep.AWAITING_TERMS, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        when(aiGateway.interpret(any())).thenReturn(new AiInterpretation(IntentType.TERMS_ACCEPTANCE, null, null));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "de acordo com os termos", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_PAYMENT_METHOD);
        verify(whatsAppMessageGateway).sendPaymentMethodOptions(phoneNumber);
    }

    @Test
    void shouldNotAdvanceTermsWhenCustomerExplicitlyDeclinesEvenIfAiMisclassifies() {
        Instant now = Instant.parse("2026-04-06T10:10:30Z");
        String phoneNumber = "+5583771111111";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(180))
            .selectService(ServiceType.DECOR, ConversationStep.AWAITING_TERMS, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "não aceito", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        verify(whatsAppMessageGateway).sendTermsOfUse(phoneNumber);
    }

    @Test
    void shouldMoveAwaitingTermsToPaymentMethodWhenCustomerAcceptsTermsByButton() {
        Instant now = Instant.parse("2026-04-06T10:11:00Z");
        String phoneNumber = "+5583770000000";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(180))
            .selectService(ServiceType.DECOR, ConversationStep.AWAITING_TERMS, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "TERMS_ACCEPT"),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_PAYMENT_METHOD);
        verify(whatsAppMessageGateway).sendPaymentMethodOptions(phoneNumber);
    }

    @Test
    void shouldRepeatCurrentStepWhenAiReturnsUnknown() {
        Instant now = Instant.parse("2026-04-06T10:15:00Z");
        String phoneNumber = "+5583666666666";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(120))
            .moveTo(ConversationStep.TRIAGE_DIRECT, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "texto aleatorio", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        verify(whatsAppMessageGateway).sendDirectTriageOptions(eq(phoneNumber), any());
    }

    @Test
    void shouldRepeatTermsWhenCustomerDeclinesTermsByButton() {
        Instant now = Instant.parse("2026-04-06T10:16:00Z");
        String phoneNumber = "+5583660000000";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(180))
            .selectService(ServiceType.DECOR, ConversationStep.AWAITING_TERMS, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "TERMS_DECLINE"),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        verify(whatsAppMessageGateway).sendTermsOfUse(phoneNumber);
    }

    @Test
    void shouldUseFreeTextPaymentMethodWithoutAi() {
        Instant now = Instant.parse("2026-04-06T10:20:00Z");
        String phoneNumber = "+5583555555555";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(240))
            .selectService(ServiceType.DECOR, ConversationStep.AWAITING_PAYMENT_METHOD, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        when(serviceCatalogGateway.findByType(ServiceType.DECOR)).thenReturn(Optional.of(decor()));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "pode ser pix", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.PAYMENT_LINK_SENT);
        assertThat(updated.context().paymentMethod()).isEqualTo("PIX");
        verify(whatsAppMessageGateway).sendPaymentLink(eq(phoneNumber), any(ServiceCatalogItem.class));
        verify(whatsAppMessageGateway).sendClosingMessage(phoneNumber);
    }

    @Test
    void shouldPersistConfirmedServiceWhenCustomerConfirmsSelection() {
        Instant now = Instant.parse("2026-04-06T10:22:00Z");
        String phoneNumber = "+5583550001111";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(240))
            .selectService(ServiceType.DECOR, ConversationStep.AWAITING_CONFIRMATION, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "CONFIRM_SERVICE"),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        assertThat(updated.context().slotValue(ConversationSlotName.CONFIRMED_SERVICE)).contains("DECOR");
    }

    @Test
    void shouldDiscardInvalidSuggestedServiceFromAiSlotUpdates() {
        Instant now = Instant.parse("2026-04-06T10:23:00Z");
        String phoneNumber = "+5583550002222";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(240))
            .moveTo(ConversationStep.SERVICE_DISCOVERY, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        when(aiGateway.converse(any())).thenReturn(new ConversationalAiReply(
            "Acho que entendi, me conta mais um pouco.",
            ConversationalAiAction.CONFIRM_UNDERSTANDING,
            List.of(new ConversationSlotUpdate(
                ConversationSlotName.SUGGESTED_SERVICE,
                "SERVICO_INVENTADO",
                ConversationSlotLevel.TENTATIVE,
                0.92,
                ConversationSlotSource.INFERRED
            )),
            0.92,
            false,
            null,
            false,
            null
        ));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "quero algo diferente", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.SERVICE_DISCOVERY);
        assertThat(updated.context().slotValue(ConversationSlotName.SUGGESTED_SERVICE)).isEmpty();
    }

    @Test
    void shouldKeepCurrentStepAndNotifyHumanWhenCustomerRequestsHumanHandoff() {
        Instant now = Instant.parse("2026-04-06T10:25:00Z");
        String phoneNumber = "+5583444444444";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(240))
            .moveTo(ConversationStep.TRIAGE_DIRECT, now.minusSeconds(60));

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);
        when(conversationMessageGateway.findRecentByConversationId(any(), eq(5))).thenReturn(List.of(
            br.com.urbana.connect.domain.conversation.model.ConversationMessage.inbound(
                "conversation-1",
                phoneNumber,
                br.com.urbana.connect.domain.conversation.model.ConversationMessageType.TEXT,
                "quero falar com alguém",
                null,
                null,
                now.minusSeconds(5),
                "TRIAGE_DIRECT"
            )
        ));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "quero falar com alguém", ""),
            now
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.TRIAGE_DIRECT);
        verify(whatsAppMessageGateway).sendHumanHandoffAcknowledgement(phoneNumber);
        ArgumentCaptor<br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest> captor =
            ArgumentCaptor.forClass(br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest.class);
        verify(humanHandoffGateway).notifyTeam(captor.capture());
        assertThat(captor.getValue().recentMessages()).containsExactly("USER: quero falar com alguém");
    }

    @Test
    void shouldIgnoreDuplicateInboundWebhookBeforeProcessingFlow() {
        Instant now = Instant.parse("2026-04-06T10:30:00Z");
        String phoneNumber = "+5583333333333";
        Conversation conversation = Conversation.start(phoneNumber, now.minusSeconds(60));

        when(conversationMessageGateway.existsByProviderMessageId("wamid-duplicate")).thenReturn(true);
        when(conversationGateway.findLatestByPhoneNumber(phoneNumber)).thenReturn(Optional.of(conversation));

        Conversation updated = conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "oi", "", "", "text", "wamid-duplicate"),
            now
        );

        assertThat(updated).isEqualTo(conversation);
        verify(conversationGateway).findLatestByPhoneNumber(phoneNumber);
    }

    @Test
    void shouldPersistInboundListReplyAsInteractiveList() {
        Instant now = Instant.parse("2026-04-06T10:35:00Z");
        String phoneNumber = "+5583222222222";
        Conversation conversation = new Conversation(
            "conversation-1",
            phoneNumber,
            br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE,
            ConversationStep.TRIAGE_GUIDED,
            null,
            br.com.urbana.connect.domain.conversation.model.ConversationContext.empty(),
            now.minusSeconds(120),
            now.minusSeconds(60),
            now.plusSeconds(86400)
        );

        when(conversationLifecycleService.resumeOrStart(phoneNumber, now)).thenReturn(conversation);

        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(phoneNumber, "", "DECOR", "Decor", "list_reply", "wamid-list-1"),
            now
        );

        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationMessageGateway).save(captor.capture());
        assertThat(captor.getValue().messageType()).isEqualTo(ConversationMessageType.INTERACTIVE_LIST);
    }

    private ServiceCatalogItem decor() {
        return new ServiceCatalogItem(
            ServiceType.DECOR,
            "Decor",
            "🛋️",
            "Renovar espaço interno sem quebra-quebra",
            "Apresentação da Decor",
            BigDecimal.valueOf(400),
            "https://mpago.la/decor",
            "https://forms.gle/decor",
            true
        );
    }
}
