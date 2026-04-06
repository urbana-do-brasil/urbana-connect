package br.com.urbana.connect.infrastructure.ai.gemini;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAiGatewayTest {

    @Test
    void shouldParseServiceSelectionFromGeminiResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiGateway gateway = new GeminiAiGateway(builder.build(), new ObjectMapper(), "test-api-key", "gemini-2.5-flash-lite");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-api-key"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string(containsString("\"responseMimeType\":\"application/json\"")))
            .andExpect(content().string(containsString("SERVICE_SELECTION")))
            .andRespond(withSuccess("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"intent\\":\\"SERVICE_SELECTION\\",\\"selectedService\\":\\"DECOR\\",\\"suggestedResponse\\":null}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        var interpretation = gateway.interpret(new AiContext(
            ConversationStep.TRIAGE_DIRECT,
            "quero decor",
            List.of(new ServiceSummary(ServiceType.DECOR, "Decor", "Renovar espaço interno")),
            ""
        ));

        assertThat(interpretation.intent()).isEqualTo(IntentType.SERVICE_SELECTION);
        assertThat(interpretation.selectedService()).isEqualTo(ServiceType.DECOR);
        server.verify();
    }

    @Test
    void shouldReturnUnknownWhenGeminiResponseIsInvalid() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiGateway gateway = new GeminiAiGateway(builder.build(), new ObjectMapper(), "test-api-key", "gemini-2.5-flash-lite");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-api-key"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "nao-e-json"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        var interpretation = gateway.interpret(new AiContext(
            ConversationStep.AWAITING_CONFIRMATION,
            "acho que sim",
            List.of(),
            ""
        ));

        assertThat(interpretation.intent()).isEqualTo(IntentType.UNKNOWN);
        assertThat(interpretation.selectedService()).isNull();
        server.verify();
    }
}
