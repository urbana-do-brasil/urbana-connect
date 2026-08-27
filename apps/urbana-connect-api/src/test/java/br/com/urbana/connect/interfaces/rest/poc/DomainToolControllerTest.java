package br.com.urbana.connect.interfaces.rest.poc;

import br.com.urbana.connect.application.reception.ActiveTurnLeaseService;
import br.com.urbana.connect.application.reception.tools.DomainToolInvocationUseCase;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DomainToolControllerTest {
    private static final String TOKEN = "internal-secret";
    private static final String PRINCIPAL = "hermes-urbana-domain";

    @Test
    void rejectsMissingOrIncorrectAuthenticationBeforeCallingDomain() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        DomainToolController.ToolRequest request = new DomainToolController.ToolRequest("session-1", PRINCIPAL, Map.of());

        assertThat(controller.invoke("get_customer_profile", null, request).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.invoke("get_customer_profile", "Bearer wrong", request).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsRuntimePrincipalNotInTheConfiguredAllowlist() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        var response = controller.invoke("get_customer_profile", "Bearer " + TOKEN,
                new DomainToolController.ToolRequest("session-1", "model-chosen", Map.of()));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsModelSuppliedTechnicalIdentifiers() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        var response = controller.invoke("prepare_terms", "Bearer " + TOKEN,
                new DomainToolController.ToolRequest("session-1", PRINCIPAL,
                        Map.of("serviceType", "DECOR", "contactId", "model-contact")));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsUnknownToolAndMissingLeaseThroughDomainBoundary() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        String authorization = "Bearer " + TOKEN;
        var request = new DomainToolController.ToolRequest("session-1", PRINCIPAL, Map.of());

        assertThat(controller.invoke("shell_exec", authorization, request).getStatusCode().value()).isEqualTo(404);
        when(useCase.invoke(any(), any(), eq(DomainToolName.GET_CUSTOMER_PROFILE), any()))
                .thenThrow(new ActiveTurnLeaseService.LeaseRejectedException("active lease is absent"));
        assertThat(controller.invoke("get_customer_profile", authorization, request).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void delegatesOnlyAllowlistedToolWithoutExposingTheInternalIdempotencyKey() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        when(useCase.invoke(eq("session-1"), eq(PRINCIPAL), eq(DomainToolName.PREPARE_TERMS), any()))
                .thenReturn(new DomainToolInvocationUseCase.InvocationResult(Map.of("status", "PRESENTED"),
                        "turn-1:prepare_terms:hash", false));
        var response = controller.invoke("prepare_terms", "Bearer " + TOKEN,
                new DomainToolController.ToolRequest("session-1", PRINCIPAL, Map.of("serviceType", "DECOR")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().ok()).isTrue();
        assertThat(response.getBody().idempotencyKey()).isNull();
        verify(useCase).invoke(eq("session-1"), eq(PRINCIPAL), eq(DomainToolName.PREPARE_TERMS),
                eq(Map.of("serviceType", "DECOR")));
    }

    @Test
    void replacesTechnicalFailureDetailsWithAStructuredSafeEnvelope() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        when(useCase.invoke(any(), any(), eq(DomainToolName.GET_CUSTOMER_PROFILE), any()))
                .thenThrow(new IllegalArgumentException("OPENROUTER_API_KEY=super-secret-token"));

        var response = controller.invoke("get_customer_profile", "Bearer " + TOKEN,
                new DomainToolController.ToolRequest("session-1", PRINCIPAL, Map.of()));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().code()).isEqualTo("BUSINESS_RULE_REJECTED");
        assertThat(response.getBody().error().customerMessage())
                .doesNotContain("super-secret-token", "OPENROUTER_API_KEY", "sistema", "ferramenta", "API",
                        "banco", "HTTP", "exceção", "retry", "idempotência", "stack");
    }

    @Test
    void returnsStableBusinessCodeNextActionAndCustomerSafeMessage() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);
        when(useCase.invoke(any(), any(), eq(DomainToolName.PREPARE_PAYMENT), any()))
                .thenThrow(new DomainToolInvocationUseCase.DomainRejectionException(
                        "TERMS_NOT_ACCEPTED", "ASK_FOR_CLEAR_ACCEPTANCE", java.util.List.of(),
                        "Antes do pagamento, preciso do seu aceite claro dos termos."));

        var response = controller.invoke("prepare_payment", "Bearer " + TOKEN,
                new DomainToolController.ToolRequest("session-1", PRINCIPAL,
                        Map.of("serviceType", "DECOR", "method", "PIX")));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().code()).isEqualTo("TERMS_NOT_ACCEPTED");
        assertThat(response.getBody().error().nextAction()).isEqualTo("ASK_FOR_CLEAR_ACCEPTANCE");
        assertThat(response.getBody().error().customerMessage())
                .isEqualTo("Antes do pagamento, preciso do seu aceite claro dos termos.");
    }

    @Test
    void rejectsOversizedRuntimeSessionAndPrincipalValuesBeforeDomainExecution() {
        DomainToolInvocationUseCase useCase = mock(DomainToolInvocationUseCase.class);
        DomainToolController controller = new DomainToolController(useCase, TOKEN, PRINCIPAL);

        var response = controller.invoke("get_customer_profile", "Bearer " + TOKEN,
                new DomainToolController.ToolRequest("x".repeat(129), PRINCIPAL, Map.of()));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(useCase);
    }
}
