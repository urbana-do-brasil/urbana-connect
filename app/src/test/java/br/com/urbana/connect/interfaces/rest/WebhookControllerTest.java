package br.com.urbana.connect.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.urbana.connect.application.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = WebhookController.class, properties = "whatsapp.webhook.verify-token=test-verify-token")
@Import(SecurityConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAcceptWebhookPayload(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "object": "whatsapp_business_account",
                      "entry": []
                    }
                    """))
            .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(output)
            .contains("Webhook recebido: object=whatsapp_business_account entries=0");
    }

    @Test
    void shouldReturnChallengeWhenVerificationTokenMatches(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "test-verify-token")
                .param("hub.challenge", "challenge-value"))
            .andExpect(status().isOk())
            .andExpect(content().string("challenge-value"));

        org.assertj.core.api.Assertions.assertThat(output)
            .contains("Webhook challenge aceito: mode=subscribe challengeLength=15");
    }

    @Test
    void shouldRejectChallengeWhenVerificationTokenDoesNotMatch() throws Exception {
        mockMvc.perform(get("/api/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "wrong-token")
                .param("hub.challenge", "challenge-value"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPayloadFromUnknownProvider(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "object": "unknown_provider",
                      "entry": []
                    }
                    """))
            .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(output)
            .contains("Webhook rejeitado: object=unknown_provider entries=0");
    }
}
