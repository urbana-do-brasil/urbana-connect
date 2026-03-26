package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.health.MongoConnectivityVerifier;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final String READY = "READY";
    private static final String NOT_READY = "NOT_READY";

    private final ApplicationAvailability applicationAvailability;
    private final MongoConnectivityVerifier mongoConnectivityVerifier;

    public HealthController(
            ApplicationAvailability applicationAvailability,
            MongoConnectivityVerifier mongoConnectivityVerifier) {
        this.applicationAvailability = applicationAvailability;
        this.mongoConnectivityVerifier = mongoConnectivityVerifier;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/readiness")
    public ResponseEntity<String> readiness() {
        ReadinessState readinessState = applicationAvailability.getReadinessState();

        if (readinessState == ReadinessState.ACCEPTING_TRAFFIC && mongoConnectivityVerifier.isAvailable()) {
            return ResponseEntity.ok(READY);
        }

        return ResponseEntity.status(503).body(NOT_READY);
    }
}
