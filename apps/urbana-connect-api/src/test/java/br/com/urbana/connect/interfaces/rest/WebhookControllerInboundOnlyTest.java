package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.application.conversation.ConversationFlowService;
import br.com.urbana.connect.application.reception.HermesWebhookMessageHandler;
import br.com.urbana.connect.application.reception.WebhookInbox;
import br.com.urbana.connect.application.config.SecurityConfig;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = WebhookController.class, properties = {
        "hermes.poc.enabled=false",
        "webhook.inbox.worker.enabled=true"
})
@Import(SecurityConfig.class)
class WebhookControllerInboundOnlyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationFlowService conversationFlowService;

    @MockitoBean
    private WebhookInbox webhookInbox;

    @MockitoBean
    private HermesWebhookMessageHandler hermesWebhookMessageHandler;

    @MockitoBean
    private AiGateway aiGateway;

    @MockitoBean
    private WhatsAppMessageGateway whatsAppMessageGateway;

    @Test
    void acknowledgesInboundWithoutRoutingToHermesOrAnyOutboundPath() throws Exception {
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "object": "whatsapp_business_account",
                      "entry": [{
                        "changes": [{
                          "value": {"messages": [{
                            "id": "wamid-inbound-only",
                            "from": "5511999999999",
                            "type": "text",
                            "text": {"body": "mensagem sintética"}
                          }]}
                        }]
                      }]
                    }
                    """))
            .andExpect(status().isOk());

        verify(webhookInbox).accept(
                eq(new InboundWhatsAppMessage("5511999999999", "mensagem sintética", "", "", "text", "wamid-inbound-only")),
                any());
        verifyNoInteractions(conversationFlowService);
        verifyNoInteractions(hermesWebhookMessageHandler);
        verifyNoInteractions(aiGateway, whatsAppMessageGateway);
    }
}
