package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnResult;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.OpenClawClient;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenClawPocServiceTest {

    @Mock
    private ConversationLifecycleService conversationLifecycleService;

    @Mock
    private ConversationMessageGateway conversationMessageGateway;

    @Mock
    private OpenClawClient openClawClient;

    @Mock
    private WhatsAppMessageGateway whatsAppMessageGateway;

    private OpenClawPocService service;

    @BeforeEach
    void setUp() {
        service = new OpenClawPocService(
            conversationLifecycleService,
            conversationMessageGateway,
            openClawClient,
            new OpenClawSessionKeyResolver(),
            new OpenClawResponseValidator(),
            whatsAppMessageGateway,
            "fallback",
            100,
            8
        );
    }

    @Test
    void shouldSendOpenClawReplyWhenClientReturnsValidText() {
        Instant now = Instant.parse("2026-05-03T10:00:00Z");
        InboundWhatsAppMessage inbound = new InboundWhatsAppMessage("+5583999991111", "oi", "", "", "text", "wamid-1");
        when(conversationLifecycleService.resumeOrStart(inbound.phoneNumber(), now))
            .thenReturn(Conversation.start(inbound.phoneNumber(), now));
        when(openClawClient.sendTurn(any())).thenReturn(OpenClawTurnResult.success("Olá!"));

        service.handleTextTurn(inbound, now);

        verify(whatsAppMessageGateway).sendTextMessage(inbound.phoneNumber(), "Olá!");
    }

    @Test
    void shouldSendFallbackWhenClientReturnsTimeout() {
        Instant now = Instant.parse("2026-05-03T10:00:00Z");
        InboundWhatsAppMessage inbound = new InboundWhatsAppMessage("+5583999991111", "oi", "", "", "text", "wamid-1");
        when(conversationLifecycleService.resumeOrStart(inbound.phoneNumber(), now))
            .thenReturn(new Conversation("conv-1", inbound.phoneNumber(),
                br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE,
                br.com.urbana.connect.domain.conversation.model.ConversationStep.GREETING,
                null,
                br.com.urbana.connect.domain.conversation.model.ConversationContext.empty(),
                now, now, now.plusSeconds(3600)));
        when(openClawClient.sendTurn(any())).thenReturn(OpenClawTurnResult.timeout("client_timeout"));

        service.handleTextTurn(inbound, now);

        verify(whatsAppMessageGateway).sendTextMessage(inbound.phoneNumber(), "fallback");
    }

    @Test
    void shouldSendFallbackWhenReplyIsTooLong() {
        Instant now = Instant.parse("2026-05-03T10:00:00Z");
        InboundWhatsAppMessage inbound = new InboundWhatsAppMessage("+5583999991111", "oi", "", "", "text", "wamid-1");
        when(conversationLifecycleService.resumeOrStart(inbound.phoneNumber(), now))
            .thenReturn(new Conversation("conv-1", inbound.phoneNumber(),
                br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE,
                br.com.urbana.connect.domain.conversation.model.ConversationStep.GREETING,
                null,
                br.com.urbana.connect.domain.conversation.model.ConversationContext.empty(),
                now, now, now.plusSeconds(3600)));
        when(openClawClient.sendTurn(any())).thenReturn(OpenClawTurnResult.success("x".repeat(101)));

        service.handleTextTurn(inbound, now);

        verify(whatsAppMessageGateway).sendTextMessage(inbound.phoneNumber(), "fallback");
    }

    @Test
    void shouldIgnoreDuplicateInboundMessage() {
        Instant now = Instant.parse("2026-05-03T10:00:00Z");
        InboundWhatsAppMessage inbound = new InboundWhatsAppMessage("+5583999991111", "oi", "", "", "text", "wamid-1");
        when(conversationLifecycleService.resumeOrStart(inbound.phoneNumber(), now))
            .thenReturn(new Conversation("conv-1", inbound.phoneNumber(),
                br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE,
                br.com.urbana.connect.domain.conversation.model.ConversationStep.GREETING,
                null,
                br.com.urbana.connect.domain.conversation.model.ConversationContext.empty(),
                now, now, now.plusSeconds(3600)));
        when(conversationMessageGateway.save(any())).thenThrow(new DuplicateKeyException("dup"));

        service.handleTextTurn(inbound, now);

        verify(openClawClient, never()).sendTurn(any());
        verify(whatsAppMessageGateway, never()).sendTextMessage(any(), any());
    }

    @Test
    void shouldSendRecentConversationHistoryToOpenClaw() {
        Instant now = Instant.parse("2026-05-03T10:00:00Z");
        InboundWhatsAppMessage inbound = new InboundWhatsAppMessage("+5583999991111", "Qual servico eu disse que queria?", "", "", "text", "wamid-2");
        Conversation conversation = new Conversation("conv-1", inbound.phoneNumber(),
            br.com.urbana.connect.domain.conversation.model.ConversationStatus.ACTIVE,
            br.com.urbana.connect.domain.conversation.model.ConversationStep.GREETING,
            null,
            br.com.urbana.connect.domain.conversation.model.ConversationContext.empty(),
            now, now, now.plusSeconds(3600));
        when(conversationLifecycleService.resumeOrStart(inbound.phoneNumber(), now)).thenReturn(conversation);
        when(conversationMessageGateway.findRecentByConversationId("conv-1", 8)).thenReturn(List.of(
            ConversationMessage.inbound("conv-1", inbound.phoneNumber(), ConversationMessageType.TEXT,
                "Meu nome e Rafael e eu estou interessado no Decor Pintura.", null, "wamid-1", now.minusSeconds(60), "GREETING"),
            ConversationMessage.outbound("conv-1", inbound.phoneNumber(), ConversationMessageType.TEXT,
                "O Decor Pintura custa R$ 250 por ambiente.", now.minusSeconds(30), "GREETING"),
            ConversationMessage.inbound("conv-1", inbound.phoneNumber(), ConversationMessageType.TEXT,
                inbound.textBody(), null, "wamid-2", now, "GREETING")
        ));
        when(openClawClient.sendTurn(any())).thenReturn(OpenClawTurnResult.success("Você tinha dito Decor Pintura."));

        service.handleTextTurn(inbound, now);

        var captor = forClass(OpenClawTurnRequest.class);
        verify(openClawClient).sendTurn(captor.capture());
        assertThat(captor.getValue().text())
            .contains("Historico recente da conversa")
            .contains("Cliente: Meu nome e Rafael e eu estou interessado no Decor Pintura.")
            .contains("Urba: O Decor Pintura custa R$ 250 por ambiente.")
            .contains("Cliente: Qual servico eu disse que queria?");
    }
}
