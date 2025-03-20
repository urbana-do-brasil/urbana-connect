package br.com.urbana.connect.application.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Configuração de cache para a aplicação.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * Configura o gerenciador de cache com Caffeine.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList("gpt-responses", "customer-data"));
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    /**
     * Configura o builder do Caffeine com políticas de expiração e tamanho.
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats();
    }
} 