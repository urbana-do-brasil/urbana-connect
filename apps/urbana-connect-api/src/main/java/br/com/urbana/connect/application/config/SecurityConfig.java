package br.com.urbana.connect.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${hermes.poc.enabled:false}") boolean pocEnabled) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .cacheControl(cache -> { })
                .contentTypeOptions(options -> { })
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; script-src 'self'; style-src 'self'; "
                                + "connect-src 'self'; img-src 'none'; base-uri 'none'; "
                                + "form-action 'self'; frame-ancestors 'none'"))
                .addHeaderWriter((request, response) -> response.setHeader(
                        "X-Robots-Tag", "noindex, nofollow")))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/health", "/api/v1/readiness", "/api/webhook", "/actuator/**").permitAll();
                // Terms are public only through the opaque bearer checked by
                // TermsConsentController; the HTML shell itself contains no
                // customer data and is safe to serve anonymously.
                auth.requestMatchers("/termos", "/termos/", "/termos/**",
                        "/api/terms/presentation", "/api/terms/end-reached",
                        "/api/terms/decision").permitAll();
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
