package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpHermesSessionsGatewayTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void classifiesEmptyOrBlankHermesContentAsAnAmbiguousTechnicalResponse(String content) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesSessionsGateway gateway = new HttpHermesSessionsGateway(
                builder.build(), "http://hermes.test", "server-secret", "openai/gpt-5.6-luna", "max");

        server.expect(requestTo("http://hermes.test/api/sessions/s1/chat"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"" + content + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.chat("s1", new HermesSessionsGateway.HermesChatRequest("oi")))
                .isInstanceOf(HttpHermesSessionsGateway.HermesSessionsException.class)
                .satisfies(error -> {
                    HttpHermesSessionsGateway.HermesSessionsException exception =
                            (HttpHermesSessionsGateway.HermesSessionsException) error;
                    assertThat(exception.phase())
                            .isEqualTo(HttpHermesSessionsGateway.HermesFailurePhase.POST_DISPATCH_AMBIGUOUS);
                });
        server.verify();
    }

    @Test
    void classifiesOnlyExplicitRejectionBeforeDispatchAsSafeToRetry() {
        HttpHermesSessionsGateway.HermesSessionsException rejected =
                HttpHermesSessionsGateway.HermesSessionsException.fromStatus(422, "prompt-secret");
        HttpHermesSessionsGateway.HermesSessionsException upstream =
                HttpHermesSessionsGateway.HermesSessionsException.fromStatus(503, "provider-secret");
        HttpHermesSessionsGateway.HermesSessionsException timeout =
                HttpHermesSessionsGateway.HermesSessionsException.fromTransport(new SocketTimeoutException("read"));

        assertThat(rejected.phase()).isEqualTo(HttpHermesSessionsGateway.HermesFailurePhase.PRE_DISPATCH);
        assertThat(rejected.retryAllowed()).isTrue();
        assertThat(upstream.phase()).isEqualTo(HttpHermesSessionsGateway.HermesFailurePhase.POST_DISPATCH_AMBIGUOUS);
        assertThat(upstream.retryAllowed()).isFalse();
        assertThat(timeout.phase()).isEqualTo(HttpHermesSessionsGateway.HermesFailurePhase.POST_DISPATCH_AMBIGUOUS);
        assertThat(timeout.retryAllowed()).isFalse();
        assertThat(upstream.getMessage()).doesNotContain("provider-secret", "prompt");
    }

    @Test
    void derivesDeterministicStableCursorForEnvelopeWithoutServerCursorWithoutExposingHistory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesSessionsGateway gateway = new HttpHermesSessionsGateway(
                builder.build(), "http://hermes.test", "server-secret", "openai/gpt-5.6-luna", "max");

        String prompt = "prompt-secret";
        String response = "assistant-secret";
        String body = "{\"object\":\"list\",\"session_id\":\"s1\",\"data\":["
                + "{\"role\":\"user\",\"content\":\"" + prompt + "\"},"
                + "{\"role\":\"assistant\",\"content\":\"" + response + "\"}]}";
        server.expect(requestTo("http://hermes.test/api/sessions/s1/messages"))
                .andExpect(method(GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hermes.test/api/sessions/s1/messages"))
                .andExpect(method(GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        String firstCursor = gateway.historySnapshot("s1").stableCursor().orElseThrow();
        String secondCursor = gateway.historySnapshot("s1").stableCursor().orElseThrow();

        assertThat(firstCursor).isEqualTo(secondCursor);
        assertThat(firstCursor).matches("[0-9a-f]{64}");
        assertThat(firstCursor).doesNotContain(prompt, response, "server-secret");
        server.verify();
    }

    @Test
    void keepsExplicitHermesCursorAheadOfDerivedCursor() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesSessionsGateway gateway = new HttpHermesSessionsGateway(
                builder.build(), "http://hermes.test", "server-secret", "openai/gpt-5.6-luna", "max");

        server.expect(requestTo("http://hermes.test/api/sessions/s1/messages"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"cursor\":\"server-cursor\",\"data\":[{\"role\":\"user\",\"content\":\"prompt\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.historySnapshot("s1").stableCursor()).contains("server-cursor");
        server.verify();
    }

    @Test
    void derivesDifferentStableCursorsForDifferentOrderedHistorySnapshots() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesSessionsGateway gateway = new HttpHermesSessionsGateway(
                builder.build(), "http://hermes.test", "server-secret", "openai/gpt-5.6-luna", "max");

        server.expect(requestTo("http://hermes.test/api/sessions/s1/messages"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "[{\"role\":\"user\",\"content\":\"same-content\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hermes.test/api/sessions/s1/messages"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"role\":\"assistant\",\"content\":\"same-content\"}]}",
                        MediaType.APPLICATION_JSON));

        String firstCursor = gateway.historySnapshot("s1").stableCursor().orElseThrow();
        String secondCursor = gateway.historySnapshot("s1").stableCursor().orElseThrow();

        assertThat(firstCursor).isNotEqualTo(secondCursor);
        assertThat(firstCursor).matches("[0-9a-f]{64}");
        assertThat(secondCursor).matches("[0-9a-f]{64}");
        server.verify();
    }

    @Test
    void followsNativeSessionsEnvelopeHeadersHistoryAndMultimodalMessageContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesSessionsGateway gateway = new HttpHermesSessionsGateway(
                builder.build(), "http://hermes.test", "secret", "openai/gpt-5.6-luna", "max");

        // Register every expected request before invoking the gateway. This
        // keeps the test strict about the exact native API sequence.
        server.expect(requestTo("http://hermes.test/api/sessions"))
                .andExpect(method(POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(content().json("{\"title\":\"contact-1\"}"))
                .andExpect(jsonPath("$.model").value("openai/gpt-5.6-luna"))
                .andExpect(jsonPath("$.provider").value("openrouter"))
                .andExpect(jsonPath("$.model_options.reasoning_effort").value("max"))
                .andRespond(withSuccess(
                        "{\"object\":\"hermes.session\",\"session\":{\"id\":\"s1\"}}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://hermes.test/api/sessions/s1/chat"))
                .andExpect(method(POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(jsonPath("$.message").value("oi"))
                .andRespond(withSuccess(
                        "{\"session_id\":\"s2\",\"message\":{\"content\":\"{\\\"message\\\":\\\"Oi\\\",\\\"nextAction\\\":\\\"NONE\\\"}\"},\"usage\":{\"input_tokens\":4,\"output_tokens\":6,\"total_tokens\":10}}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://hermes.test/api/sessions/s2/chat"))
                .andExpect(method(POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(jsonPath("$.message[0].type").value("text"))
                .andExpect(jsonPath("$.message[1].type").value("image_url"))
                .andExpect(jsonPath("$.message[1].image_url.url").value("data:image/png;base64,AAAA"))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"ok\"},\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}",
                        MediaType.APPLICATION_JSON).header("X-Hermes-Session-Id", "s3"));

        server.expect(requestTo("http://hermes.test/api/sessions/s3/messages"))
                .andExpect(method(GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andRespond(withSuccess(
                        "{\"object\":\"list\",\"session_id\":\"s3\",\"data\":[{\"role\":\"user\",\"content\":\"oi\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.createSession("contact-1")).isEqualTo("s1");
        HermesSessionsGateway.HermesChatResult result = gateway.chat("s1",
                new HermesSessionsGateway.HermesChatRequest("oi"));
        assertThat(result.effectiveSessionId()).isEqualTo("s2");
        assertThat(result.content()).contains("nextAction");
        assertThat(result.usage().inputTokens()).isEqualTo(4);
        assertThat(result.usage().outputTokens()).isEqualTo(6);

        HermesSessionsGateway.HermesChatResult multimodal = gateway.chat("s2",
                new HermesSessionsGateway.HermesChatRequest("foto",
                        List.of("data:image/png;base64,AAAA"),
                        "openai/gpt-5.6-luna", "openrouter", "max"));
        assertThat(multimodal.effectiveSessionId()).isEqualTo("s3");
        assertThat(multimodal.usage().totalTokens()).isEqualTo(3);
        assertThat(gateway.history("s3")).hasSize(1);
        server.verify();
    }

    @Test
    void doesNotForwardARepositoryFixturePathAsAnExternalImageUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesSessionsGateway gateway = new HttpHermesSessionsGateway(
                builder.build(), "http://hermes.test", "secret", "openai/gpt-5.6-luna", "max");

        server.expect(requestTo("http://hermes.test/api/sessions/s1/chat"))
                .andExpect(method(POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(jsonPath("$.message").value("comprovante recebido"))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"ok\"}}", MediaType.APPLICATION_JSON));

        gateway.chat("s1", new HermesSessionsGateway.HermesChatRequest(
                "comprovante recebido", List.of("poc/payment-proof-fixture.svg"),
                "openai/gpt-5.6-luna", "openrouter", "max"));

        server.verify();
    }
}
