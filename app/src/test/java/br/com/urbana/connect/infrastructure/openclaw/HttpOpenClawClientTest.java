package br.com.urbana.connect.infrastructure.openclaw;

import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpOpenClawClientTest {

    @Test
    void shouldReturnSuccessWhenBridgeRespondsWithOk() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8787");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenClawClient client = new HttpOpenClawClient(builder.build(), "/conversation-turn");

        server.expect(requestTo("http://localhost:8787/conversation-turn"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {
                  "sessionKey": "whatsapp:5583999991111",
                  "text": "Oi",
                  "from": "+5583999991111",
                  "conversationId": "conv-1",
                  "timestamp": "2026-05-03T10:00:00Z"
                }
                """, true))
            .andRespond(withSuccess("""
                {
                  "status": "ok",
                  "text": "Olá! Como posso te ajudar?"
                }
                """, MediaType.APPLICATION_JSON));

        var result = client.sendTurn(new OpenClawTurnRequest(
            "whatsapp:5583999991111",
            "Oi",
            "+5583999991111",
            "conv-1",
            "2026-05-03T10:00:00Z"
        ));

        assertThat(result.status()).isEqualTo(OpenClawTurnStatus.SUCCESS);
        assertThat(result.text()).isEqualTo("Olá! Como posso te ajudar?");
        server.verify();
    }

    @Test
    void shouldReturnTimeoutWhenBridgeRespondsWithTimeoutStatus() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8787");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenClawClient client = new HttpOpenClawClient(builder.build(), "/conversation-turn");

        server.expect(requestTo("http://localhost:8787/conversation-turn"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "status": "timeout",
                  "errorReason": "gateway_timeout"
                }
                """, MediaType.APPLICATION_JSON));

        var result = client.sendTurn(new OpenClawTurnRequest(
            "whatsapp:5583999991111",
            "Oi",
            "+5583999991111",
            "conv-1",
            "2026-05-03T10:00:00Z"
        ));

        assertThat(result.status()).isEqualTo(OpenClawTurnStatus.TIMEOUT);
        assertThat(result.errorReason()).isEqualTo("gateway_timeout");
        server.verify();
    }
}
