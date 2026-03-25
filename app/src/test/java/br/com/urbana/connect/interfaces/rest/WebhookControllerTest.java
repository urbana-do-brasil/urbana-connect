package br.com.urbana.connect.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.urbana.connect.application.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(SecurityConfig.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAcceptWebhookPayload() throws Exception {
        mockMvc.perform(post("/api/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "object": "whatsapp_business_account",
                      "entry": []
                    }
                    """))
            .andExpect(status().isOk());
    }
}
