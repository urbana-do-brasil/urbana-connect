package br.com.urbana.connect.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${hermes.poc.enabled:false}") boolean pocEnabled) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/health", "/api/v1/readiness", "/api/webhook", "/actuator/**").permitAll();
                // The controller performs the bearer-token check; only its
                // intended HTTP method is reachable anonymously.
                auth.requestMatchers(HttpMethod.POST, "/internal/poc/domain-tools/**").permitAll();
                if (pocEnabled) {
                    auth.requestMatchers("/api/poc/conversations/**").permitAll();
                }
                auth.anyRequest().authenticated();
            });
        return http.build();
    }
}
