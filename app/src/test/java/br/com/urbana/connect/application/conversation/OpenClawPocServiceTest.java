package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnResult;
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

import static org.mockito.ArgumentMatchers.any;
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
            100
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
}
