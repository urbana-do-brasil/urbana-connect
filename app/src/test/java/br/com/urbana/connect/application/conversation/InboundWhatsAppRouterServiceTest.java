package br.com.urbana.connect.application.conversation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InboundWhatsAppRouterServiceTest {

    @Test
    void shouldKeepLegacyFlowWhenFeatureFlagIsDisabled() {
        ConversationFlowService conversationFlowService = mock(ConversationFlowService.class);
        OpenClawPocService openClawPocService = mock(OpenClawPocService.class);
        InboundWhatsAppRouterService routerService = new InboundWhatsAppRouterService(
            conversationFlowService,
            openClawPocService,
            false
        );

        InboundWhatsAppMessage message = new InboundWhatsAppMessage("+5583999991111", "oi", "", "", "text", "wamid-1");
        routerService.handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));

        verify(conversationFlowService).handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));
        verify(openClawPocService, never()).handleTextTurn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldDelegateTextDmToOpenClawWhenFeatureFlagIsEnabled() {
        ConversationFlowService conversationFlowService = mock(ConversationFlowService.class);
        OpenClawPocService openClawPocService = mock(OpenClawPocService.class);
        InboundWhatsAppRouterService routerService = new InboundWhatsAppRouterService(
            conversationFlowService,
            openClawPocService,
            true
        );

        InboundWhatsAppMessage message = new InboundWhatsAppMessage("+5583999991111", "oi", "", "", "text", "wamid-1");
        routerService.handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));

        verify(openClawPocService).handleTextTurn(message, Instant.parse("2026-05-03T10:00:00Z"));
        verify(conversationFlowService, never()).handleIncomingMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldKeepLegacyFlowForMediaWhenFeatureFlagIsEnabled() {
        ConversationFlowService conversationFlowService = mock(ConversationFlowService.class);
        OpenClawPocService openClawPocService = mock(OpenClawPocService.class);
        InboundWhatsAppRouterService routerService = new InboundWhatsAppRouterService(
            conversationFlowService,
            openClawPocService,
            true
        );

        InboundWhatsAppMessage message = new InboundWhatsAppMessage("+5583999991111", "", "", "", "image", "wamid-1");
        routerService.handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));

        verify(conversationFlowService).handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));
        verify(openClawPocService, never()).handleTextTurn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldKeepLegacyFlowForGroupMessageWhenFeatureFlagIsEnabled() {
        ConversationFlowService conversationFlowService = mock(ConversationFlowService.class);
        OpenClawPocService openClawPocService = mock(OpenClawPocService.class);
        InboundWhatsAppRouterService routerService = new InboundWhatsAppRouterService(
            conversationFlowService,
            openClawPocService,
            true
        );

        InboundWhatsAppMessage message = new InboundWhatsAppMessage("120363111111111@g.us", "oi", "", "", "text", "wamid-1");
        routerService.handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));

        verify(conversationFlowService).handleIncomingMessage(message, Instant.parse("2026-05-03T10:00:00Z"));
        verify(openClawPocService, never()).handleTextTurn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
