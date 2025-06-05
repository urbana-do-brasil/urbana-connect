package br.com.urbana.connect.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public class WireMockConfig {

    private static WireMockServer wireMockServer;
    public static final int WIREMOCK_PORT = 8089; // Porta padrão para o WireMock

    /**
     * Inicia o servidor WireMock se ainda não estiver em execução.
     * Configura o servidor para usar a porta definida em WIREMOCK_PORT.
     */
    public static void startServer() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .port(WIREMOCK_PORT)
                .usingFilesUnderClasspath("wiremock"));
            wireMockServer.start();
            // Configura o cliente HTTP para usar a URL do WireMock globalmente para os testes, se necessário
            // System.setProperty("whatsapp.api.base-url", wireMockServer.baseUrl());
        }
    }

    /**
     * Para o servidor WireMock se estiver em execução.
     */
    public static void stopServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            // Opcional: limpar a propriedade do sistema se foi definida
            // System.clearProperty("whatsapp.api.base-url");
        }
    }

    /**
     * Reseta todos os mapeamentos de stub e o log de requisições no servidor WireMock.
     * Útil para garantir que os testes não interfiram uns nos outros.
     */
    public static void resetAll() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        } else {
            // Inicia o servidor se estiver parado e um reset for solicitado
            startServer();
            wireMockServer.resetAll();
        }
    }

    /**
     * Retorna a URL base do servidor WireMock em execução.
     * @return A URL base, por exemplo, "http://localhost:8089".
     * @throws IllegalStateException se o servidor WireMock não estiver em execução.
     */
    public static String getBaseUrl() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            // Considerar iniciar automaticamente ou lançar uma exceção mais específica
            // Para robustez, podemos tentar iniciar aqui se o objetivo é sempre ter um servidor.
            startServer(); 
        }
        return wireMockServer.baseUrl();
    }

    /**
     * Retorna a instância do WireMockServer.
     * Inicia o servidor se não estiver rodando.
     * Isso permite que os testes configurem stubs diretamente.
     * Ex: WireMockConfig.getWireMockServer().stubFor(...)
     * @return A instância do WireMockServer.
     */
    public static WireMockServer getWireMockServer() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            startServer();
        }
        return wireMockServer;
    }
} 