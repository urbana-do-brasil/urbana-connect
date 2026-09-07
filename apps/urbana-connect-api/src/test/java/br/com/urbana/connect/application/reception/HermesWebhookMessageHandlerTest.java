package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationRegistryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HermesWebhookMessageHandlerTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-11T12:00:00Z");

    @Mock
    private ReceptionOrchestrator orchestrator;

    @Mock
    private WhatsAppMessageGateway whatsapp;

    @Mock
    private DeliveryOutboxGateway outbox;

    @Mock
    private DeliveryDestinationRegistryGateway destinationRegistry;

    private HermesWebhookMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HermesWebhookMessageHandler(orchestrator, whatsapp,
                new HermesFailureHandoffService(outbox, Clock.fixed(RECEIVED_AT, java.time.ZoneOffset.UTC)));
    }

    @Test
    void sendsTheExactHermesTextThroughWhatsAppAfterCanonicalProcessing() {
        String exactReply = "  Retorno do Hermes: acentuação, pontuação!\nsegunda linha  ";
        InboundWhatsAppMessage message = new InboundWhatsAppMessage(
                "5511999999999", "Quero conhecer os serviços", "", "", "text", "wamid-1");
        AgentOutput output = new AgentOutput(exactReply, AgentNextAction.NONE);
        when(orchestrator.process(any())).thenReturn(receipt(
                "wamid-1", "correlation-1", ReceptionOrchestrator.TurnStatus.COMPLETED, output));

        handler.handle(message, RECEIVED_AT);

        ArgumentCaptor<InboundConversationEvent> eventCaptor =
                ArgumentCaptor.forClass(InboundConversationEvent.class);
        verify(orchestrator).process(eventCaptor.capture());
        InboundConversationEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("wamid-1");
        assertThat(event.contactId()).startsWith("wa:").doesNotContain("5511999999999");
        assertThat(event.type()).isEqualTo(ReceptionMessageType.TEXT);
        assertThat(event.text()).isEqualTo("Quero conhecer os serviços");
        assertThat(event.providerMessageId()).isEqualTo("wamid-1");
        verify(whatsapp).sendTextMessage("5511999999999", exactReply);
    }

    @Test
    void doesNotSendWhenTheTurnIsDuplicate() {
        AgentOutput output = new AgentOutput("resposta já persistida", AgentNextAction.NONE);
        when(orchestrator.process(any())).thenReturn(receipt(
                "wamid-2", "correlation-2", ReceptionOrchestrator.TurnStatus.DUPLICATE, output));

        handler.handle(new InboundWhatsAppMessage(
                "5511888888888", "mensagem repetida", "", "", "text", "wamid-2"), RECEIVED_AT);

        verify(whatsapp, never()).sendTextMessage(any(), any());
    }

    @Test
    void recordsHumanHandoffAndSendsTheFixedSafeMessageWhenTheTurnIsInconclusive() {
        when(orchestrator.process(any())).thenReturn(receipt(
                "wamid-3", "correlation-3", ReceptionOrchestrator.TurnStatus.RECONCILING, null));

        handler.handle(new InboundWhatsAppMessage(
                "5511777777777", "mensagem ambígua", "", "", "text", "wamid-3"), RECEIVED_AT);

        verify(whatsapp).sendTextMessage("5511777777777",
                HermesWebhookMessageHandler.SAFE_HUMAN_HANDOFF_MESSAGE);
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).saveIfAbsent(outboxCaptor.capture());
        DeliveryOutbox intent = outboxCaptor.getAllValues().getFirst();
        assertThat(intent.eventKey()).isEqualTo("hermes-failure:wamid-3:human-handoff");
        assertThat(intent.kind()).isEqualTo(DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF);
        assertThat(intent.destinationRef()).isEqualTo(HermesFailureHandoffService.SUPPORT_MAILBOX_DESTINATION);
        assertThat(intent.payload()).contains("reason=HERMES_RECONCILING")
                .contains("contactRef=wa:").doesNotContain("5511777777");
    }

    @Test
    void recordsHumanHandoffAndSendsTheFixedSafeMessageWhenHermesThrows() {
        InboundWhatsAppMessage message = new InboundWhatsAppMessage(
                "5511666666666", "preciso de ajuda", "", "", "text", "wamid-4");
        when(orchestrator.process(any())).thenThrow(new IllegalStateException("Hermes indisponível"));

        handler.handle(message, RECEIVED_AT);

        verify(outbox, org.mockito.Mockito.times(2)).saveIfAbsent(any(DeliveryOutbox.class));
        verify(whatsapp).sendTextMessage("5511666666666",
                HermesWebhookMessageHandler.SAFE_HUMAN_HANDOFF_MESSAGE);
    }

    @Test
    void doesNotPromiseHumanHandoffWhenItsDurableIntentCannotBeRecorded() {
        when(orchestrator.process(any())).thenReturn(receipt(
                "wamid-5", "correlation-5", ReceptionOrchestrator.TurnStatus.FAILED, null));
        doThrow(new IllegalStateException("Mongo indisponível"))
                .when(outbox).saveIfAbsent(any(DeliveryOutbox.class));

        handler.handle(new InboundWhatsAppMessage(
                "5511555555555", "preciso de ajuda", "", "", "text", "wamid-5"), RECEIVED_AT);

        verify(whatsapp, never()).sendTextMessage(any(), any());
    }

    @Test
    void ignoresUnknownMessageTypesButQueuesPaymentProofMediaForOfficialWorker() {
        PocReceptionWorker worker = org.mockito.Mockito.mock(PocReceptionWorker.class);
        HermesWebhookMessageHandler official = new HermesWebhookMessageHandler(worker, destinationRegistry);

        official.handle(new InboundWhatsAppMessage(
                "5511333333333", "", "", "", "image", "wamid-image"), RECEIVED_AT);
        official.handle(new InboundWhatsAppMessage(
                "5511333333333", "", "", "", "payment_proof", "wamid-proof"), RECEIVED_AT);

        ArgumentCaptor<java.util.List<InboundConversationEvent>> events = ArgumentCaptor.forClass(java.util.List.class);
        verify(worker, org.mockito.Mockito.times(2)).enqueue(events.capture());
        assertThat(events.getAllValues()).extracting(value -> value.getFirst().type())
                .containsExactly(ReceptionMessageType.IMAGE, ReceptionMessageType.PAYMENT_PROOF);
        assertThat(events.getAllValues()).extracting(value -> value.getFirst().mediaFixture())
                .containsExactly("wamid-image", "wamid-proof");
        verify(orchestrator, never()).process(any());
        verify(whatsapp, never()).sendTextMessage(any(), any());

        official.handle(new InboundWhatsAppMessage(
                "5511333333333", "", "", "", "sticker", "wamid-sticker"), RECEIVED_AT);
        verify(worker, org.mockito.Mockito.times(2)).enqueue(any());
    }

    @Test
    void officialHandlerQueuesTheCanonicalEventWithoutSendingWhatsAppDirectly() {
        PocReceptionWorker worker = org.mockito.Mockito.mock(PocReceptionWorker.class);
        HermesWebhookMessageHandler official = new HermesWebhookMessageHandler(worker, destinationRegistry);

        official.handle(new InboundWhatsAppMessage(
                "5511444444444", "quero orçamento", "", "", "text", "wamid-official"), RECEIVED_AT);

        ArgumentCaptor<java.util.List<InboundConversationEvent>> events = ArgumentCaptor.forClass(java.util.List.class);
        verify(worker).enqueue(events.capture());
        verify(destinationRegistry).saveIfAbsent(
                org.mockito.ArgumentMatchers.startsWith("wa:"),
                org.mockito.ArgumentMatchers.eq("5511444444444"));
        assertThat(events.getValue()).singleElement().satisfies(event -> {
            assertThat(event.contactId()).startsWith("wa:");
            assertThat(event.contactId()).doesNotContain("5511444444444");
        });
        verify(whatsapp, never()).sendTextMessage(any(), any());
    }

    private static ReceptionOrchestrator.TurnReceipt receipt(
            String eventId, String correlationId, ReceptionOrchestrator.TurnStatus status, AgentOutput output) {
        return new ReceptionOrchestrator.TurnReceipt(eventId, correlationId, status, output, null);
    }
}
