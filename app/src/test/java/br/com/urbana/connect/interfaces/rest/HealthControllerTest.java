package br.com.urbana.connect.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.urbana.connect.application.config.SecurityConfig;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationAvailability applicationAvailability;

    @Test
    void shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
    }

    @Test
    void shouldReturnReadyWhenApplicationAcceptsTraffic() throws Exception {
        given(applicationAvailability.getReadinessState()).willReturn(ReadinessState.ACCEPTING_TRAFFIC);

        mockMvc.perform(get("/api/v1/readiness"))
            .andExpect(status().isOk())
            .andExpect(content().string("READY"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenApplicationIsNotReady() throws Exception {
        given(applicationAvailability.getReadinessState()).willReturn(ReadinessState.REFUSING_TRAFFIC);

        mockMvc.perform(get("/api/v1/readiness"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string("NOT_READY"));
    }
}
