package br.com.urbana.connect.infrastructure.openclaw;

import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpOpenClawClientTest {

    @Test
    void shouldReturnSuccessWhenGatewayRespondsWithChatCompletion() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18789");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenClawClient client = new HttpOpenClawClient(
            builder.build(),
            "/v1/chat/completions",
            "gateway-token",
            "openclaw/urba"
        );

        server.expect(requestTo("http://localhost:18789/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer gateway-token"))
            .andExpect(header("x-openclaw-session-key", "agent:urba:whatsapp:wa_1234567890abcdef"))
            .andExpect(header("x-openclaw-message-channel", "whatsapp"))
            .andExpect(content().json("""
                {
                  "model": "openclaw/urba",
                  "stream": false,
                  "user": "agent:urba:whatsapp:wa_1234567890abcdef",
                  "messages": [
                    {
                      "role": "user",
                      "content": "Quais servicos voces oferecem?"
                    }
                  ]
                }
                """, true))
            .andRespond(withSuccess("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Oferecemos Decor, Decor Pintura, Decor Fachada e Decor Reforma."
                      }
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        var result = client.sendTurn(new OpenClawTurnRequest(
            "agent:urba:whatsapp:wa_1234567890abcdef",
            "Quais servicos voces oferecem?",
            "+5583999991111",
            "conv-1",
            "2026-05-03T10:00:00Z"
        ));

        assertThat(result.status()).isEqualTo(OpenClawTurnStatus.SUCCESS);
        assertThat(result.text()).isEqualTo("Oferecemos Decor, Decor Pintura, Decor Fachada e Decor Reforma.");
        server.verify();
    }

    @Test
    void shouldReturnErrorWhenGatewayResponseHasNoText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18789");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenClawClient client = new HttpOpenClawClient(
            builder.build(),
            "/v1/chat/completions",
            "gateway-token",
            "openclaw/urba"
        );

        server.expect(requestTo("http://localhost:18789/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "choices": []
                }
                """, MediaType.APPLICATION_JSON));

        var result = client.sendTurn(new OpenClawTurnRequest(
            "agent:urba:whatsapp:wa_1234567890abcdef",
            "Oi",
            "+5583999991111",
            "conv-1",
            "2026-05-03T10:00:00Z"
        ));

        assertThat(result.status()).isEqualTo(OpenClawTurnStatus.ERROR);
        assertThat(result.errorReason()).isEqualTo("invalid_gateway_response");
        server.verify();
    }

    @Test
    void shouldReturnGatewayHttpErrorWhenGatewayFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18789");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenClawClient client = new HttpOpenClawClient(
            builder.build(),
            "/v1/chat/completions",
            "gateway-token",
            "openclaw/urba"
        );

        server.expect(requestTo("http://localhost:18789/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        var result = client.sendTurn(new OpenClawTurnRequest(
            "agent:urba:whatsapp:wa_1234567890abcdef",
            "Oi",
            "+5583999991111",
            "conv-1",
            "2026-05-03T10:00:00Z"
        ));

        assertThat(result.status()).isEqualTo(OpenClawTurnStatus.ERROR);
        assertThat(result.errorReason()).isEqualTo("gateway_http_500");
        server.verify();
    }

    @Test
    void shouldReturnTimeoutWhenGatewayRequestTimesOut() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:18789");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenClawClient client = new HttpOpenClawClient(
            builder.build(),
            "/v1/chat/completions",
            "gateway-token",
            "openclaw/urba"
        );

        server.expect(requestTo("http://localhost:18789/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(request -> {
                throw new ResourceAccessException("timeout");
            });

        var result = client.sendTurn(new OpenClawTurnRequest(
            "agent:urba:whatsapp:wa_1234567890abcdef",
            "Oi",
            "+5583999991111",
            "conv-1",
            "2026-05-03T10:00:00Z"
        ));

        assertThat(result.status()).isEqualTo(OpenClawTurnStatus.TIMEOUT);
        assertThat(result.errorReason()).isEqualTo("client_timeout");
        server.verify();
    }
}
