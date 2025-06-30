package br.com.urbana.connect.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@TestConfiguration
public class WireMockConfig {

    public static final int WIREMOCK_PORT = 8089;

    /**
     * Cria e gerencia um bean do WireMockServer.
     * O Spring cuidará do ciclo de vida (iniciar/parar).
     * O escopo 'singleton' garante que a mesma instância seja usada em todos os testes.
     */
    @Bean(destroyMethod = "stop")
    @Scope("singleton")
    public WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options()
                .port(WIREMOCK_PORT)
                .usingFilesUnderClasspath("wiremock")); // Carrega stubs de test/resources/wiremock
        server.start();
        return server;
    }
} 