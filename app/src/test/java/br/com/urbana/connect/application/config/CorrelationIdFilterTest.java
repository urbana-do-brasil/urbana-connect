package br.com.urbana.connect.application.config;

import br.com.urbana.connect.application.health.MongoConnectivityVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.urbana.connect.interfaces.rest.HealthController;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationAvailability applicationAvailability;

    @MockitoBean
    private MongoConnectivityVerifier mongoConnectivityVerifier;

    @Test
    void shouldReuseIncomingCorrelationId() throws Exception {
        given(applicationAvailability.getReadinessState()).willReturn(ReadinessState.ACCEPTING_TRAFFIC);

        mockMvc.perform(get("/api/v1/health")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "test-correlation-id"))
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "test-correlation-id"));
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsMissing() throws Exception {
        given(applicationAvailability.getReadinessState()).willReturn(ReadinessState.ACCEPTING_TRAFFIC);

        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }
}
