package br.com.urbana.connect.interfaces.rest.poc;

import br.com.urbana.connect.application.reception.InboundConversationEvent;
import br.com.urbana.connect.application.reception.MediaNormalizationService;
import br.com.urbana.connect.application.reception.MessageBatcher;
import br.com.urbana.connect.application.reception.PocReceptionIngress;
import br.com.urbana.connect.application.reception.ReceptionMetrics;
import br.com.urbana.connect.application.reception.ReceptionOrchestrator;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.HermesResumeGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Local synthetic ingress; it deliberately does not touch the real webhook. */
@RestController
@RequestMapping("/api/poc/conversations")
@ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
public class ConversationSimulatorController {
    private static final int MAX_TEXT_LENGTH = 8000;
    private static final int MAX_TRANSCRIPT_LENGTH = 12000;
    private static final int MAX_MEDIA_REFERENCE_LENGTH = 512;
    private static final Pattern CONTACT_ALIAS = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern FIXTURE_PATH = Pattern.compile("poc/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*(?:\\.[A-Za-z0-9_-]+)?");
    private final ReceptionOrchestrator orchestrator;
    private final PocReceptionIngress ingress;
    private final String expectedToken;
    private final HermesResumeGateway resumeGateway;

    public ConversationSimulatorController(ReceptionOrchestrator orchestrator) {
        this(orchestrator, new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService()), "", null);
    }

    public ConversationSimulatorController(ReceptionOrchestrator orchestrator, PocReceptionIngress ingress) {
        this(orchestrator, ingress, "", null);
    }

    @Autowired
    public ConversationSimulatorController(ReceptionOrchestrator orchestrator, PocReceptionIngress ingress,
                                           @Value("${hermes.poc.api-token:}") String expectedToken,
                                           HermesResumeGateway resumeGateway) {
        this.orchestrator = orchestrator;
        this.ingress = ingress;
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
        this.resumeGateway = resumeGateway;
    }

    public ConversationSimulatorController(ReceptionOrchestrator orchestrator, PocReceptionIngress ingress,
                                           String expectedToken) {
        this(orchestrator, ingress, expectedToken, null);
    }

    @PostMapping("/{contactAlias}/messages")
    public ResponseEntity<ReceptionOrchestrator.TurnReceipt> receive(
            @PathVariable String contactAlias,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SyntheticInboundEvent request) {
        if (!authorized(authorization)) {
            return unauthorized();
        }
        if (!CONTACT_ALIAS.matcher(contactAlias).matches()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            InboundConversationEvent event = request.toCanonical("poc:" + contactAlias);
            ReceptionOrchestrator.TurnReceipt receipt = ingress.accept(event);
            HttpStatus status = receipt.status() == ReceptionOrchestrator.TurnStatus.DUPLICATE
                    ? HttpStatus.CONFLICT : HttpStatus.ACCEPTED;
            return ResponseEntity.status(status).body(receipt);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{contactAlias}/flush")
    public ResponseEntity<List<ReceptionOrchestrator.TurnReceipt>> forceFlush(
            @PathVariable String contactAlias,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!authorized(authorization)) {
            return unauthorized();
        }
        if (!CONTACT_ALIAS.matcher(contactAlias).matches()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ingress.forceFlush("poc:" + contactAlias));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ReceptionMetrics.Snapshot> metrics(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!authorized(authorization)) {
            return unauthorized();
        }
        return ResponseEntity.ok(orchestrator.metrics().snapshot());
    }

    @GetMapping("/{contactAlias}")
    public ResponseEntity<Map<String, Object>> projection(
            @PathVariable String contactAlias,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!authorized(authorization)) {
            return unauthorized();
        }
        if (!CONTACT_ALIAS.matcher(contactAlias).matches()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(orchestrator.projection("poc:" + contactAlias));
    }

    @PostMapping("/{contactAlias}/payment-proof/approve")
    public ResponseEntity<ReceptionOrchestrator.TurnReceipt> approvePaymentProof(
            @PathVariable String contactAlias,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!authorized(authorization)) {
            return unauthorized();
        }
        if (!CONTACT_ALIAS.matcher(contactAlias).matches()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(orchestrator.approvePaymentProof("poc:" + contactAlias));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{contactAlias}/human/messages")
    public ResponseEntity<ReceptionOrchestrator.HumanMessageReceipt> recordHumanMessage(
            @PathVariable String contactAlias,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody HumanOperatorMessage request) {
        if (!authorized(authorization)) return unauthorized();
        if (!validAlias(contactAlias) || idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(orchestrator.recordHumanMessage("poc:" + contactAlias,
                    idempotencyKey.trim(), request.text(), request.occurredAt()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{contactAlias}/ownership/urba")
    public ResponseEntity<ReceptionOrchestrator.ResumeReceipt> returnToUrba(
            @PathVariable String contactAlias,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ResumeOwnershipRequest request) {
        if (!authorized(authorization)) return unauthorized();
        if (!validAlias(contactAlias) || idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        long expectedVersion = request == null || request.expectedVersion() == null
                ? -1 : request.expectedVersion();
        ReceptionOrchestrator.ResumeReceipt receipt = orchestrator.returnToUrba(
                "poc:" + contactAlias, idempotencyKey.trim(), expectedVersion, resumeGateway);
        if (receipt.status() == br.com.urbana.connect.domain.reception.model.ResumeStatus.COMPLETED
                || receipt.duplicate()) {
            return ResponseEntity.ok(receipt);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(receipt);
    }

    private static boolean validAlias(String contactAlias) {
        return contactAlias != null && CONTACT_ALIAS.matcher(contactAlias).matches();
    }

    private boolean authorized(String authorization) {
        if (expectedToken.isBlank()) {
            return true;
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String presented = authorization.substring("Bearer ".length()).trim();
        return !presented.isBlank() && MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> ResponseEntity<T> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SyntheticInboundEvent(
            String eventId,
            ReceptionMessageType type,
            String text,
            String transcript,
            String mediaFixture,
            String interactiveReplyId,
            Instant occurredAt) {
        InboundConversationEvent toCanonical(String contactId) {
            if (eventId == null || eventId.isBlank() || type == null || occurredAt == null) {
                throw new IllegalArgumentException("eventId, type and occurredAt are required");
            }
            if (eventId.length() > 128 || length(text) > MAX_TEXT_LENGTH
                    || length(transcript) > MAX_TRANSCRIPT_LENGTH
                    || length(mediaFixture) > MAX_MEDIA_REFERENCE_LENGTH
                    || length(interactiveReplyId) > 256) {
                throw new IllegalArgumentException("synthetic event exceeds the POC size limit");
            }
            if (mediaFixture != null && !mediaFixture.isBlank()
                    && (!FIXTURE_PATH.matcher(mediaFixture).matches() || hasTraversalSegment(mediaFixture))) {
                throw new IllegalArgumentException("mediaFixture is not an allowlisted fixture path");
            }
            return new InboundConversationEvent(eventId, contactId, type, text, transcript, mediaFixture,
                    interactiveReplyId, occurredAt, "poc:" + eventId);
        }

        private static boolean hasTraversalSegment(String value) {
            return java.util.Arrays.stream(value.split("/"))
                    .anyMatch(segment -> segment.equals(".") || segment.equals(".."));
        }

        private static int length(String value) {
            return value == null ? 0 : value.length();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record HumanOperatorMessage(String text, Instant occurredAt) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ResumeOwnershipRequest(Long expectedVersion) { }
}
