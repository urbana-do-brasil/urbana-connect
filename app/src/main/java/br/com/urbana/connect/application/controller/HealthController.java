package br.com.urbana.connect.application.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para verificar a saúde da aplicação.
 * Fornece endpoints para monitoramento e verificação de status dos componentes.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController implements HealthIndicator {

    private final CacheManager cacheManager;

    /**
     * Endpoint para verificar o status da aplicação.
     * Retorna informações básicas sobre o status do serviço.
     *
     * @return Resposta com o status da aplicação
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Urbana Connect WhatsApp Chatbot");
        response.put("timestamp", System.currentTimeMillis());
        
        // Informações sobre o cache
        Map<String, Object> cacheInfo = new HashMap<>();
        cacheManager.getCacheNames().forEach(name -> {
            cacheInfo.put(name, "available");
        });
        response.put("cache", cacheInfo);
        
        log.debug("Requisição de verificação de saúde realizada");
        return ResponseEntity.ok(response);
    }

    /**
     * Implementação do HealthIndicator para integração com o Actuator.
     *
     * @return Health com o status da aplicação
     */
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        details.put("service", "Urbana Connect WhatsApp Chatbot");
        details.put("cache_names", cacheManager.getCacheNames());
        
        return Health.up().withDetails(details).build();
    }
} 