package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    private HermesWebhookMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HermesWebhookMessageHandler(orchestrator, whatsapp);
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
    void doesNotSendWhenTheTurnIsInconclusiveOrFailed() {
        when(orchestrator.process(any())).thenReturn(receipt(
                "wamid-3", "correlation-3", ReceptionOrchestrator.TurnStatus.RECONCILING, null));

        handler.handle(new InboundWhatsAppMessage(
                "5511777777777", "mensagem ambígua", "", "", "text", "wamid-3"), RECEIVED_AT);

        verify(whatsapp, never()).sendTextMessage(any(), any());
    }

    private static ReceptionOrchestrator.TurnReceipt receipt(
            String eventId, String correlationId, ReceptionOrchestrator.TurnStatus status, AgentOutput output) {
        return new ReceptionOrchestrator.TurnReceipt(eventId, correlationId, status, output, null);
    }
}
