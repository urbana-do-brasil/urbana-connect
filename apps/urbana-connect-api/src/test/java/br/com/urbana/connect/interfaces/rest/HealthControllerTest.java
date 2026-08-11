package br.com.urbana.connect.interfaces.rest;

import java.io.IOException;
import java.util.List;

import br.com.urbana.connect.application.health.MongoConnectivityVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.urbana.connect.application.config.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;
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

    @MockitoBean
    private MongoConnectivityVerifier mongoConnectivityVerifier;

    @Test
    void shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
    }

    @Test
    void shouldReturnReadyWhenApplicationAcceptsTraffic() throws Exception {
        given(applicationAvailability.getReadinessState()).willReturn(ReadinessState.ACCEPTING_TRAFFIC);
        given(mongoConnectivityVerifier.isAvailable()).willReturn(true);

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

    @Test
    void shouldReturnServiceUnavailableWhenMongoIsUnavailable() throws Exception {
        given(applicationAvailability.getReadinessState()).willReturn(ReadinessState.ACCEPTING_TRAFFIC);
        given(mongoConnectivityVerifier.isAvailable()).willReturn(false);

        mockMvc.perform(get("/api/v1/readiness"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string("NOT_READY"));
    }

    @Test
    void shouldDisableOptionalMailHealthIndicatorInPocProfile() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> propertySources = loader.load(
            "application-poc", new ClassPathResource("application-poc.yml"));

        assertThat(propertySources)
            .anySatisfy(propertySource -> assertThat(
                propertySource.getProperty("management.health.mail.enabled"))
                .isEqualTo(false));
    }
}
