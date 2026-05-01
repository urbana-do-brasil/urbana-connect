package br.com.urbana.connect.infrastructure.ai.gemini;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotValue;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

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

    @Test
    void shouldParseConversationalReplyFromGeminiResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAiGateway gateway = new GeminiAiGateway(builder.build(), new ObjectMapper(), "test-api-key", "gemini-2.5-flash-lite");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-api-key"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("ASK_CLARIFYING_QUESTION")))
            .andExpect(content().string(containsString("needsDiscoveryHelp")))
            .andRespond(withSuccess("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"replyText\\":\\"Entendi. Você quer ajuda para descobrir o melhor serviço?\\",\\"action\\":\\"ASK_CLARIFYING_QUESTION\\",\\"slotUpdates\\":[{\\"slot\\":\\"needsDiscoveryHelp\\",\\"value\\":\\"true\\",\\"level\\":\\"CONFIRMED\\",\\"confidence\\":0.93,\\"source\\":\\"EXPLICIT\\"}],\\"confidence\\":0.93,\\"shouldAdvance\\":true,\\"suggestedNextStep\\":\\"ICP_QUALIFICATION\\",\\"shouldOfferStructuredOptions\\":false,\\"fallbackReason\\":null}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        var reply = gateway.converse(new AiContext(
            ConversationStep.GREETING,
            "preciso de ajuda",
            List.of(new ServiceSummary(ServiceType.DECOR, "Decor", "Renovar espaço interno")),
            "USER: oi",
            Map.of(ConversationSlotName.NEEDS_DISCOVERY_HELP,
                new ConversationSlotValue("true", ConversationSlotLevel.CONFIRMED, ConversationSlotSource.EXPLICIT, 1.0))
        ));

        assertThat(reply.replyText()).contains("ajuda");
        assertThat(reply.action()).isEqualTo(ConversationalAiAction.ASK_CLARIFYING_QUESTION);
        assertThat(reply.shouldAdvance()).isTrue();
        assertThat(reply.suggestedNextStep()).isEqualTo(ConversationStep.ICP_QUALIFICATION);
        assertThat(reply.slotUpdates()).singleElement().satisfies(slot -> {
            assertThat(slot.slot()).isEqualTo(ConversationSlotName.NEEDS_DISCOVERY_HELP);
            assertThat(slot.value()).isEqualTo("true");
        });
        server.verify();
    }

    @Test
    void shouldReturnConversationalFallbackWhenApiKeyIsMissing() {
        GeminiAiGateway gateway = new GeminiAiGateway(RestClient.builder().build(), new ObjectMapper(), "", "gemini-2.5-flash-lite");

        var reply = gateway.converse(new AiContext(
            ConversationStep.ICP_QUALIFICATION,
            "oi",
            List.of(),
            ""
        ));

        assertThat(reply.fallbackReason()).isEqualTo("missing_api_key");
        assertThat(reply.shouldAdvance()).isFalse();
    }

    @Test
    void shouldReturnConversationalFallbackWhenGeminiPayloadIsInvalid() {
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

        var reply = gateway.converse(new AiContext(
            ConversationStep.SERVICE_DISCOVERY,
            "quero renovar a sala",
            List.of(),
            ""
        ));

        assertThat(reply.fallbackReason()).isEqualTo("exception");
        assertThat(reply.shouldAdvance()).isFalse();
        server.verify();
    }
}
