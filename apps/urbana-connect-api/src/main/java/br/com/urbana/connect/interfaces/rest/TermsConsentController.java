package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.reception.TermsConsentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Same-origin, token-authenticated edge for the terms consent page.  No
 * customer or bearer material is written to logs or echoed in error bodies.
 */
@RestController
@RequestMapping
@ConditionalOnProperty(name = "terms.consent.enabled", havingValue = "true")
public final class TermsConsentController {
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";
    private final TermsConsentService service;

    public TermsConsentController(TermsConsentService service) {
        this.service = service;
    }

    /** Explicit mapping keeps `/termos` stable even when static resource handling changes. */
    @GetMapping(value = {"/termos", "/termos/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> termsShell() {
        return withSecurityHeaders(ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/termos/index.html")));
    }

    @PostMapping("/api/terms/presentation")
    public ResponseEntity<?> presentation(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        try {
            TermsConsentService.Presentation result = service.present(token(authorization));
            return withSecurityHeaders(ResponseEntity.ok(result));
        } catch (TermsConsentService.InvalidTokenException invalid) {
            return error(HttpStatus.UNAUTHORIZED, "TERMS_TOKEN_INVALID");
        } catch (TermsConsentService.ExpiredSessionException expired) {
            return error(HttpStatus.GONE, "TERMS_SESSION_EXPIRED");
        } catch (TermsConsentService.TermsContentUnavailableException unavailable) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "TERMS_CONTENT_UNAVAILABLE");
        } catch (TermsConsentService.BindingMismatchException mismatch) {
            return error(HttpStatus.CONFLICT, "TERMS_BINDING_CONFLICT");
        } catch (IllegalStateException state) {
            return error(HttpStatus.CONFLICT, "TERMS_STATE_CONFLICT");
        }
    }

    @PostMapping("/api/terms/end-reached")
    public ResponseEntity<?> endReached(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        try {
            service.endReached(token(authorization));
            return withSecurityHeaders(ResponseEntity.noContent().build());
        } catch (TermsConsentService.InvalidTokenException invalid) {
            return error(HttpStatus.UNAUTHORIZED, "TERMS_TOKEN_INVALID");
        } catch (TermsConsentService.ExpiredSessionException expired) {
            return error(HttpStatus.GONE, "TERMS_SESSION_EXPIRED");
        } catch (TermsConsentService.BindingMismatchException mismatch) {
            return error(HttpStatus.CONFLICT, "TERMS_BINDING_CONFLICT");
        } catch (TermsConsentService.DecisionConflictException conflict) {
            return error(HttpStatus.CONFLICT, "TERMS_STATE_CONFLICT");
        } catch (IllegalStateException state) {
            return error(HttpStatus.CONFLICT, "TERMS_STATE_CONFLICT");
        }
    }

    @PostMapping(value = "/api/terms/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> decision(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) DecisionRequest request) {
        if (request == null || request.decision() == null || request.decision().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "TERMS_DECISION_REQUIRED");
        }
        final TermsConsentService.Decision decision;
        try {
            decision = TermsConsentService.Decision.valueOf(request.decision().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return error(HttpStatus.BAD_REQUEST, "TERMS_DECISION_INVALID");
        }
        try {
            TermsConsentService.DecisionResult result = service.decide(token(authorization), decision);
            return withSecurityHeaders(ResponseEntity.ok(result));
        } catch (TermsConsentService.InvalidTokenException invalid) {
            return error(HttpStatus.UNAUTHORIZED, "TERMS_TOKEN_INVALID");
        } catch (TermsConsentService.ExpiredSessionException expired) {
            return error(HttpStatus.GONE, "TERMS_SESSION_EXPIRED");
        } catch (TermsConsentService.BindingMismatchException mismatch) {
            return error(HttpStatus.CONFLICT, "TERMS_BINDING_CONFLICT");
        } catch (TermsConsentService.DecisionConflictException conflict) {
            return error(HttpStatus.CONFLICT, "TERMS_DECISION_CONFLICT");
        } catch (IllegalStateException state) {
            return error(HttpStatus.CONFLICT, "TERMS_DECISION_CONFLICT");
        }
    }

    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new TermsConsentService.InvalidTokenException();
        }
        String value = authorization.substring("Bearer ".length()).trim();
        if (value.isBlank() || value.length() > 256) {
            throw new TermsConsentService.InvalidTokenException();
        }
        return value;
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code) {
        return withSecurityHeaders(ResponseEntity.status(status).body(new ErrorResponse(code)));
    }

    private static <T> ResponseEntity<T> withSecurityHeaders(ResponseEntity.BodyBuilder builder, T body) {
        HttpHeaders headers = securityHeaders();
        return builder.headers(values -> values.putAll(headers)).body(body);
    }

    private static <T> ResponseEntity<T> withSecurityHeaders(ResponseEntity<T> response) {
        HttpHeaders headers = securityHeaders();
        HttpHeaders target = new HttpHeaders();
        target.putAll(response.getHeaders());
        headers.forEach((name, values) -> target.putIfAbsent(name, values));
        return new ResponseEntity<>(response.getBody(), target, response.getStatusCode());
    }

    private static HttpHeaders securityHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore().mustRevalidate().cachePublic().getHeaderValue());
        // CacheControl.cachePublic() is intentionally not used: overwrite the
        // generated value below so no shared cache can retain this response.
        headers.setCacheControl("no-store, no-cache, max-age=0, must-revalidate");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", CSP);
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Robots-Tag", "noindex, nofollow");
        return headers;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DecisionRequest(String decision) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String code) { }
}
