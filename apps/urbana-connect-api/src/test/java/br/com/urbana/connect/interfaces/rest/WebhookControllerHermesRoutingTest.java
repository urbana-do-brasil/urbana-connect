package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.conversation.ConversationFlowService;
import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.application.reception.HermesWebhookMessageHandler;
import br.com.urbana.connect.application.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = WebhookController.class)
@Import(SecurityConfig.class)
class WebhookControllerHermesRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationFlowService legacyConversationFlowService;

    @MockitoBean
    private HermesWebhookMessageHandler hermesWebhookMessageHandler;

    @Test
    void routesWhatsAppMessageToHermesHandlerWhenItIsAvailable() throws Exception {
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "object": "whatsapp_business_account",
                      "entry": [{
                        "changes": [{
                          "value": {"messages": [{
                            "id": "wamid-hermes",
                            "from": "5511999999999",
                            "type": "text",
                            "text": {"body": "Quero falar com a Urba"}
                          }]}
                        }]
                      }]
                    }
                    """))
            .andExpect(status().isOk());

        verify(hermesWebhookMessageHandler).handle(
                eq(new InboundWhatsAppMessage("5511999999999", "Quero falar com a Urba", "", "", "text", "wamid-hermes")),
                any());
        verify(legacyConversationFlowService, never()).handleIncomingMessage(any(), any());
    }
}
