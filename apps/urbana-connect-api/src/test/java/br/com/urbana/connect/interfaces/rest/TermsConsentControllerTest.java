package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.config.SecurityConfig;
import br.com.urbana.connect.application.reception.TermsConsentService;
import br.com.urbana.connect.domain.reception.model.TermsConsentDecision;
import br.com.urbana.connect.domain.reception.model.TermsConsentSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = TermsConsentController.class, properties = "terms.consent.enabled=true")
@Import(SecurityConfig.class)
class TermsConsentControllerTest {
    private static final String TOKEN = "opaque-token-that-is-never-returned-by-the-api";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TermsConsentService service;

    @Test
    void servesTheTermsShellWithoutAuthenticationAndWithSecurityHeaders() throws Exception {
        mockMvc.perform(get("/termos/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    }

    @Test
    void rejectsMissingBearerBeforeCallingTheConsentService() throws Exception {
        mockMvc.perform(post("/api/terms/presentation"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        verify(service, never()).present(any());
    }

    @Test
    void returnsStructuredEscapedContentAndNeverEchoesTheBearer() throws Exception {
        TermsConsentService.Presentation presentation = new TermsConsentService.Presentation(
                "presentation-1", "DECOR_INTERIORES", "terms-v1", "hash-1",
                "https://legal.example/decor", "<script>alert(1)</script> Termos aprovados",
                TermsConsentSessionStatus.PAGE_PRESENTED, null);
        given(service.present(TOKEN)).willReturn(presentation);

        mockMvc.perform(post("/api/terms/presentation")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentationId").value("presentation-1"))
                .andExpect(jsonPath("$.content").value("<script>alert(1)</script> Termos aprovados"))
                .andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void mapsExpiredAndDecisionConflictToSafeResponses() throws Exception {
        given(service.endReached(TOKEN)).willThrow(new TermsConsentService.ExpiredSessionException());
        mockMvc.perform(post("/api/terms/end-reached")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TERMS_SESSION_EXPIRED"));

        given(service.decide(TOKEN, TermsConsentService.Decision.ACCEPT))
                .willThrow(new TermsConsentService.DecisionConflictException("ACCEPT requires END_REACHED"));
        mockMvc.perform(post("/api/terms/decision")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMS_DECISION_CONFLICT"));
    }

    @Test
    void acceptsOnlyTheExplicitDecisionEnumAndReturnsIdempotencyFlag() throws Exception {
        given(service.decide(TOKEN, TermsConsentService.Decision.ACCEPT))
                .willReturn(new TermsConsentService.DecisionResult("presentation-1",
                        TermsConsentSessionStatus.ACCEPTED, TermsConsentDecision.ACCEPT, true,
                        TermsConsentService.PAYMENT_OPTIONS_MESSAGE));

        mockMvc.perform(post("/api/terms/decision")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(true))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.continuationMessage").value(TermsConsentService.PAYMENT_OPTIONS_MESSAGE));
    }
}
