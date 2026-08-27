package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.port.out.HermesResumeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpHermesResumeGatewayTest {

    @Test
    void synchronizesFullTranscriptAndFactsAndNormalizesBaseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesResumeGateway gateway = new HttpHermesResumeGateway(
                builder, "http://hermes.test/", "resume-secret");

        server.expect(requestTo("http://hermes.test/api/internal/v1/sessions/session-1/context-sync"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer resume-secret"))
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.resumeId").value("resume-1"))
                .andExpect(jsonPath("$.messages[0].content").value("Oi"))
                .andExpect(jsonPath("$.facts[0].type").value("OCCUPATION"))
                .andRespond(withSuccess(
                        "{\"resumeId\":\"resume-1\",\"lineageId\":\"lineage-1\","
                                + "\"effectiveSessionId\":\"session-2\",\"checksum\":\"sha256:abc\","
                                + "\"cursor\":17,\"coveredThroughSequence\":4}",
                        MediaType.APPLICATION_JSON));

        HermesResumeGateway.ContextSyncReceipt receipt = gateway.synchronize("session-1",
                new HermesResumeGateway.ResumeContext(1, "resume-1", "lineage-1", "idem-1",
                        "HUMAN_TO_URBA", 12, 4, "sha256:abc",
                        List.of(new HermesResumeGateway.ContextMessage(1, "m-1", "CUSTOMER", "user", "Oi")),
                        List.of(new HermesResumeGateway.ContextFact("OCCUPATION", "designer", "CONFIRMED"))));

        assertThat(receipt).isEqualTo(new HermesResumeGateway.ContextSyncReceipt(
                "resume-1", "lineage-1", "session-2", "sha256:abc", 17, 4));
        server.verify();
    }

    @Test
    void decidesResumeAndSupportsNullableAndTextualMessages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesResumeGateway gateway = new HttpHermesResumeGateway(
                builder, "http://hermes.test", "secret");
        HermesResumeGateway.ContextSyncReceipt receipt = new HermesResumeGateway.ContextSyncReceipt(
                "resume-1", "lineage-1", "session-1", "sha256:abc", 17, 4);

        server.expect(requestTo("http://hermes.test/api/internal/v1/sessions/session-1/resume-decide"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.resumeId").value("resume-1"))
                .andExpect(jsonPath("$.contextReceipt.effectiveSessionId").value("session-1"))
                .andExpect(jsonPath("$.contextReceipt.coveredThroughSequence").value(4))
                .andExpect(jsonPath("$.directive.nextStep").value("briefing"))
                .andRespond(withSuccess(
                        "{\"resumeId\":\"resume-1\",\"effectiveSessionId\":\"session-1\","
                                + "\"action\":\"SEND_MESSAGE\",\"nextStep\":\"BRIEFING\","
                                + "\"message\":null,\"evidenceMessageIds\":[\"m-4\"],"
                                + "\"reasonCode\":\"HUMAN_RETURNED\",\"confidence\":0.91}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hermes.test/api/internal/v1/sessions/session-1/resume-decide"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"resumeId\":\"resume-1\",\"effectiveSessionId\":\"session-1\","
                                + "\"action\":\"WAIT\",\"nextStep\":\"PAYMENT\","
                                + "\"message\":\"Aguardo seu retorno.\",\"evidenceMessageIds\":[],"
                                + "\"reasonCode\":\"WAITING_CUSTOMER\",\"confidence\":0.5}",
                        MediaType.APPLICATION_JSON));

        HermesResumeGateway.ResumeDecision send = gateway.decide("session-1",
                new HermesResumeGateway.ResumeCommand(1, "resume-1", "lineage-1", "idem-1", receipt,
                        Map.of("nextStep", "briefing")));
        HermesResumeGateway.ResumeDecision wait = gateway.decide("session-1",
                new HermesResumeGateway.ResumeCommand(1, "resume-1", "lineage-1", "idem-1", receipt,
                        Map.of()));

        assertThat(send.action()).isEqualTo(HermesResumeGateway.Action.SEND_MESSAGE);
        assertThat(send.message()).isNull();
        assertThat(send.evidenceMessageIds()).containsExactly("m-4");
        assertThat(send.confidence()).isEqualTo(0.91);
        assertThat(wait.action()).isEqualTo(HermesResumeGateway.Action.WAIT);
        assertThat(wait.message()).isEqualTo("Aguardo seu retorno.");
        server.verify();
    }

    @Test
    void classifiesHttpFailuresWithTheirStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesResumeGateway gateway = new HttpHermesResumeGateway(
                builder, "http://hermes.test", null);

        server.expect(requestTo("http://hermes.test/api/internal/v1/sessions/session-1/context-sync"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.synchronize("session-1",
                new HermesResumeGateway.ResumeContext(1, "r", "l", "i", "mode", 0, 0, "checksum",
                        List.of())))
                .isInstanceOf(HttpHermesResumeGateway.ResumeGatewayException.class)
                .satisfies(error -> assertThat(((HttpHermesResumeGateway.ResumeGatewayException) error).status())
                        .isEqualTo(500));
        server.verify();
    }

    @Test
    void classifiesMalformedResponsesAsTransportFailuresAndRejectsInvalidRequiredFields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHermesResumeGateway gateway = new HttpHermesResumeGateway(builder, "   ", "secret");

        server.expect(requestTo("http://127.0.0.1:8642/api/internal/v1/sessions/s/context-sync"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.synchronize("s",
                new HermesResumeGateway.ResumeContext(1, "r", "l", "i", "mode", 0, 0, "checksum",
                        List.of())))
                .isInstanceOf(HttpHermesResumeGateway.ResumeGatewayException.class)
                .satisfies(error -> assertThat(((HttpHermesResumeGateway.ResumeGatewayException) error).status())
                        .isZero());

        RestClient.Builder secondBuilder = RestClient.builder();
        MockRestServiceServer secondServer = MockRestServiceServer.bindTo(secondBuilder).build();
        HttpHermesResumeGateway secondGateway = new HttpHermesResumeGateway(secondBuilder, null, "secret");
        secondServer.expect(requestTo("http://127.0.0.1:8642/api/internal/v1/sessions/s/context-sync"))
                .andRespond(withSuccess("{\"resumeId\":\" \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> secondGateway.synchronize("s",
                new HermesResumeGateway.ResumeContext(1, "r", "l", "i", "mode", 0, 0, "checksum",
                        List.of())))
                .isInstanceOf(HttpHermesResumeGateway.ResumeGatewayException.class)
                .satisfies(error -> assertThat(((HttpHermesResumeGateway.ResumeGatewayException) error).status())
                        .isZero());
        secondServer.verify();

        RestClient.Builder thirdBuilder = RestClient.builder();
        MockRestServiceServer thirdServer = MockRestServiceServer.bindTo(thirdBuilder).build();
        HttpHermesResumeGateway thirdGateway = new HttpHermesResumeGateway(thirdBuilder,
                "http://hermes.test", "secret");
        thirdServer.expect(requestTo("http://hermes.test/api/internal/v1/sessions/s/context-sync"))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> thirdGateway.synchronize("s",
                new HermesResumeGateway.ResumeContext(1, "r", "l", "i", "mode", 0, 0, "checksum",
                        List.of())))
                .isInstanceOf(HttpHermesResumeGateway.ResumeGatewayException.class)
                .satisfies(error -> assertThat(((HttpHermesResumeGateway.ResumeGatewayException) error).status())
                        .isZero());
        thirdServer.verify();
    }
}
