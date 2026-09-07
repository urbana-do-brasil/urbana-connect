package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEvent;
import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEventType;
import br.com.urbana.connect.domain.reception.model.TermsConsentDecision;
import br.com.urbana.connect.domain.reception.model.TermsConsentSession;
import br.com.urbana.connect.domain.reception.model.TermsConsentSessionStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditEventGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentSessionGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsContentGateway;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the auditable web terms flow.  It owns token
 * lifecycle and the domain transition, while all external effects are only
 * represented as outbox records.
 */
public class TermsConsentService {
    public static final String ACCEPT_BUTTON_LABEL = "Li e aceito os termos";
    public static final String DECLINE_BUTTON_LABEL = "Não aceito";
    public static final String PAYMENT_OPTIONS_MESSAGE =
            "Termos aceitos. Para continuar, escolha uma forma de pagamento: PIX ou cartão de crédito.";
    public static final String MENU_MESSAGE =
            "Tudo bem. Vou retornar você ao menu de serviços.";

    private static final int TOKEN_BYTES = 32;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private final TermsConsentSessionGateway sessions;
    private final TermsConsentAuditEventGateway auditEvents;
    private final DeliveryOutboxGateway outbox;
    private final ReceptionConversationGateway conversations;
    private final TermsContentGateway contentGateway;
    private final Clock clock;
    private final String publicBaseUrl;
    private final byte[] tokenSecret;
    private final Duration ttl;
    private final SecureRandom random;

    /** Constructor used by focused tests and simple adapters. */
    public TermsConsentService(TermsConsentSessionGateway sessions,
                               TermsConsentAuditEventGateway auditEvents,
                               DeliveryOutboxGateway outbox,
                               ReceptionConversationGateway conversations,
                               Clock clock,
                               String publicBaseUrl,
                               String tokenSecret) {
        this(sessions, auditEvents, outbox, conversations, null, clock, publicBaseUrl,
                tokenSecret, DEFAULT_TTL, new SecureRandom());
    }

    public TermsConsentService(TermsConsentSessionGateway sessions,
                               TermsConsentAuditEventGateway auditEvents,
                               DeliveryOutboxGateway outbox,
                               ReceptionConversationGateway conversations,
                               TermsContentGateway contentGateway,
                               Clock clock,
                               String publicBaseUrl,
                               String tokenSecret,
                               Duration ttl) {
        this(sessions, auditEvents, outbox, conversations, contentGateway, clock,
                publicBaseUrl, tokenSecret, ttl, new SecureRandom());
    }

    TermsConsentService(TermsConsentSessionGateway sessions,
                        TermsConsentAuditEventGateway auditEvents,
                        DeliveryOutboxGateway outbox,
                        ReceptionConversationGateway conversations,
                        TermsContentGateway contentGateway,
                        Clock clock,
                        String publicBaseUrl,
                        String tokenSecret,
                        Duration ttl,
                        SecureRandom random) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.conversations = conversations;
        this.contentGateway = contentGateway;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.publicBaseUrl = validateBaseUrl(publicBaseUrl);
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new IllegalArgumentException("terms token secret must not be blank");
        }
        this.tokenSecret = tokenSecret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl == null ? DEFAULT_TTL : ttl;
        if (this.ttl.isZero() || this.ttl.isNegative()) {
            throw new IllegalArgumentException("terms session ttl must be positive");
        }
        this.random = Objects.requireNonNull(random, "random");
    }

    @Transactional
    public Issued issue(IssueRequest request) {
        Objects.requireNonNull(request, "request");
        TermsContentGateway.TermsContent resolved = resolveContent(request);
        Instant now = clock.instant();
        String presentationId = "terms-" + UUID.randomUUID();
        String token = generateToken();
        String tokenDigest = digestToken(token);
        String termsHash = sha256(resolved.content());
        TermsConsentSession session = TermsConsentSession.issued(
                presentationId, request.conversationId(), request.contactId(), request.destinationRef(),
                request.contractingUnitId(), request.environment(), request.serviceType(), resolved.version(),
                termsHash, resolved.resource(), resolved.content(), tokenDigest, now, now.plus(ttl));

        TermsConsentAuditEvent issuedEvent = event(session, TermsConsentAuditEventType.ISSUED, now, null);
        TermsConsentSession persisted = sessions.saveIfAbsent(session);
        auditEvents.appendIfAbsent(issuedEvent);
        outbox.saveIfAbsent(DeliveryOutbox.pending(
                "terms:" + persisted.presentationId() + ":link", DeliveryOutboxKind.WHATSAPP_TERMS_LINK,
                persisted.destinationRef(), buildUrl(token), request.correlationId(), now));
        activateConversationConsent(persisted, now);
        return new Issued(persisted.presentationId(), token, buildUrl(token), persisted);
    }

    /** Records the browser opening the page and returns plain structured text. */
    @Transactional
    public Presentation present(String token) {
        TermsConsentSession current = active(token);
        validateConversationBinding(current);
        Instant now = clock.instant();
        if (current.status() == TermsConsentSessionStatus.ISSUED) {
            TermsConsentSession presented = current.pagePresented(now);
            auditEvents.appendIfAbsent(event(presented, TermsConsentAuditEventType.PAGE_PRESENTED, now, null));
            current = sessions.saveExpected(presented, currentVersion(current));
        }
        return toPresentation(current);
    }

    /** Records the separate end-of-content evidence used to unlock acceptance. */
    @Transactional
    public SessionResult endReached(String token) {
        TermsConsentSession current = active(token);
        validateConversationBinding(current);
        Instant now = clock.instant();
        if (current.status() == TermsConsentSessionStatus.END_REACHED
                || current.status() == TermsConsentSessionStatus.ACCEPTED
                || current.status() == TermsConsentSessionStatus.DECLINED) {
            return new SessionResult(current, true);
        }
        TermsConsentSession reached = current.endReached(now);
        auditEvents.appendIfAbsent(event(reached, TermsConsentAuditEventType.END_REACHED, now, null));
        return new SessionResult(sessions.saveExpected(reached, currentVersion(current)), false);
    }

    /**
     * Persists the decision and its continuation as one domain transaction.
     * The method never calls Hermes, WhatsApp or SMTP; those effects are
     * represented only by an idempotent outbox record.
     */
    @Transactional
    public DecisionResult decide(String token, Decision decision) {
        Objects.requireNonNull(decision, "decision");
        TermsConsentSession current = active(token);
        validateConversationBinding(current);
        Instant now = clock.instant();
        if (current.status() == TermsConsentSessionStatus.ACCEPTED
                || current.status() == TermsConsentSessionStatus.DECLINED) {
            TermsConsentDecision previous = current.decision();
            if (previous != decision.asDomain()) {
                throw new DecisionConflictException("opposite terms decision already recorded");
            }
            return result(current, true);
        }
        String decisionEventId = current.presentationId() + ":" + decision.name();
        TermsConsentSession decided;
        try {
            decided = decision == Decision.ACCEPT
                    ? current.accept(now, decisionEventId) : current.decline(now, decisionEventId);
        } catch (IllegalStateException rejection) {
            if (rejection.getMessage() != null && rejection.getMessage().contains("expired")) {
                throw new ExpiredSessionException();
            }
            throw new DecisionConflictException(rejection.getMessage());
        }
        validateConversationBinding(decided);
        auditEvents.appendIfAbsent(event(decided,
                decision == Decision.ACCEPT ? TermsConsentAuditEventType.ACCEPTED
                        : TermsConsentAuditEventType.DECLINED,
                now, decisionEventId));
        TermsConsentSession saved = sessions.saveExpected(decided, currentVersion(current));
        transitionConversation(saved, decision, now);
        DeliveryOutboxKind kind = decision == Decision.ACCEPT
                ? DeliveryOutboxKind.WHATSAPP_PAYMENT_OPTIONS : DeliveryOutboxKind.WHATSAPP_MENU;
        String payload = decision == Decision.ACCEPT ? PAYMENT_OPTIONS_MESSAGE : MENU_MESSAGE;
        outbox.saveIfAbsent(DeliveryOutbox.pending(
                "terms:" + saved.presentationId() + (decision == Decision.ACCEPT
                        ? ":payment-options" : ":menu"), kind, saved.destinationRef(), payload,
                saved.presentationId(), now));
        return result(saved, false);
    }

    /** Alias used by REST adapters that prefer explicit method naming. */
    public Presentation load(String token) {
        return present(token);
    }

    public String digest(String token) {
        return digestToken(token);
    }

    /**
     * Verifies the authoritative web decision that unlocks payment.  This is
     * deliberately backed by the consent-session projection, not by the
     * legacy conversation-text audit, so a WhatsApp message can never become
     * an alternative source of contractual consent.
     */
    public TermsConsentSession requireAcceptedEvidence(ReceptionConversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
        String presentationId = conversation.activeTermsConsentId();
        if (presentationId == null || presentationId.isBlank()) {
            throw new IllegalStateException("durable web terms acceptance evidence is missing");
        }
        TermsConsentSession session = sessions.findByPresentationId(presentationId)
                .orElseThrow(() -> new IllegalStateException("durable web terms acceptance evidence is missing"));
        validateConversationBinding(session);
        if (session.status() != TermsConsentSessionStatus.ACCEPTED
                || session.decision() != TermsConsentDecision.ACCEPT) {
            throw new IllegalStateException("durable web terms acceptance evidence is not accepted");
        }
        return session;
    }

    private DecisionResult result(TermsConsentSession session, boolean idempotent) {
        return new DecisionResult(session.presentationId(), session.status(), session.decision(), idempotent,
                session.decision() == TermsConsentDecision.ACCEPT ? PAYMENT_OPTIONS_MESSAGE : MENU_MESSAGE);
    }

    private Presentation toPresentation(TermsConsentSession session) {
        return new Presentation(session.presentationId(), session.serviceType(), session.termsVersion(),
                session.termsHash(), session.termsResource(), session.termsContent(), session.status(),
                session.decision());
    }

    private TermsConsentSession active(String token) {
        if (token == null || token.isBlank() || token.length() < 32 || token.length() > 256) {
            throw new InvalidTokenException();
        }
        TermsConsentSession session = sessions.findByTokenDigest(digestToken(token))
                .orElseThrow(InvalidTokenException::new);
        Instant now = clock.instant();
        if (session.expiredAt(now)
                && session.status() != TermsConsentSessionStatus.ACCEPTED
                && session.status() != TermsConsentSessionStatus.DECLINED) {
            TermsConsentSession expired = session.expire(now);
            auditEvents.appendIfAbsent(event(expired, TermsConsentAuditEventType.EXPIRED, now, null));
            sessions.saveExpected(expired, currentVersion(session));
            throw new ExpiredSessionException();
        }
        if (session.status() == TermsConsentSessionStatus.EXPIRED
                || session.status() == TermsConsentSessionStatus.INVALIDATED) {
            throw new ExpiredSessionException();
        }
        return session;
    }

    private TermsContentGateway.TermsContent resolveContent(IssueRequest request) {
        if (request.termsVersion() != null && !request.termsVersion().isBlank()
                && request.termsContent() != null && !request.termsContent().isBlank()) {
            return new TermsContentGateway.TermsContent(request.termsVersion(), request.termsResource(),
                    request.termsContent());
        }
        if (contentGateway != null) {
            return contentGateway.find(request.serviceType(), request.termsResource())
                    .orElseThrow(TermsContentUnavailableException::new);
        }
        throw new TermsContentUnavailableException();
    }

    private void activateConversationConsent(TermsConsentSession session, Instant now) {
        if (conversations == null) {
            return;
        }
        ReceptionConversation conversation = conversations.findByContactId(session.contactId())
                .orElseThrow(() -> new BindingMismatchException("conversation is not available"));
        validateBinding(conversation, session);
        if (conversation.activeTermsConsentId() == null) {
            conversations.saveExpected(conversation.activateTermsConsent(session.presentationId(), now),
                    conversation.version());
        } else if (!session.presentationId().equals(conversation.activeTermsConsentId())) {
            // A new service/version presentation supersedes an older pending
            // session. Decided sessions remain immutable legal evidence, while
            // the conversation points only at the newest session.
            sessions.findByPresentationId(conversation.activeTermsConsentId()).ifPresent(previous -> {
                if (previous.status() != TermsConsentSessionStatus.ACCEPTED
                        && previous.status() != TermsConsentSessionStatus.DECLINED
                        && previous.status() != TermsConsentSessionStatus.EXPIRED
                        && previous.status() != TermsConsentSessionStatus.INVALIDATED) {
                    TermsConsentSession invalidated = previous.invalidate(now);
                    auditEvents.appendIfAbsent(event(invalidated,
                            TermsConsentAuditEventType.INVALIDATED, now, null));
                    sessions.saveExpected(invalidated, currentVersion(previous));
                }
            });
            conversations.saveExpected(conversation.replaceTermsConsent(session.presentationId(), now),
                    conversation.version());
        }
    }

    private void transitionConversation(TermsConsentSession session, Decision decision, Instant now) {
        if (conversations == null) {
            return;
        }
        ReceptionConversation conversation = conversations.findByContactId(session.contactId())
                .orElseThrow(() -> new BindingMismatchException("conversation is not available"));
        validateBinding(conversation, session);
        if (conversation.activeTermsConsentId() == null
                || !session.presentationId().equals(conversation.activeTermsConsentId())) {
            throw new BindingMismatchException("active terms presentation does not match");
        }
        ReceptionConversation transitioned = decision == Decision.ACCEPT
                ? conversation.acceptTerms(now) : conversation.declineTerms(now);
        conversations.saveExpected(transitioned, conversation.version());
    }

    private void validateConversationBinding(TermsConsentSession session) {
        if (conversations == null) {
            return;
        }
        ReceptionConversation conversation = conversations.findByContactId(session.contactId())
                .orElseThrow(() -> new BindingMismatchException("conversation is not available"));
        validateBinding(conversation, session);
        if (conversation.activeTermsConsentId() != null
                && !session.presentationId().equals(conversation.activeTermsConsentId())) {
            throw new BindingMismatchException("active terms presentation does not match");
        }
    }

    private static void validateBinding(ReceptionConversation conversation, TermsConsentSession session) {
        if (!conversation.id().equals(session.conversationId())
                || !conversation.contactId().equals(session.contactId())
                || !Objects.equals(conversation.contractingUnitId(), session.contractingUnitId())
                || !Objects.equals(conversation.selectedService(), session.serviceType())
                || !Objects.equals(conversation.environmentLabel(), session.environment())) {
            throw new BindingMismatchException("terms binding does not match current conversation");
        }
        if (conversation.mode() != br.com.urbana.connect.domain.reception.model.ReceptionMode.AI) {
            throw new BindingMismatchException("human-owned conversation cannot accept terms");
        }
    }

    private TermsConsentAuditEvent event(TermsConsentSession session, TermsConsentAuditEventType type,
                                         Instant at, String decisionEventId) {
        return TermsConsentAuditEvent.create(session.presentationId(), type, session.conversationId(),
                session.contactId(), session.destinationRef(), session.contractingUnitId(), session.environment(),
                session.serviceType(), session.termsVersion(), session.termsHash(), session.termsResource(),
                at, decisionEventId);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digestToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception impossible) {
            throw new IllegalStateException("terms token digest unavailable", impossible);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String buildUrl(String token) {
        return publicBaseUrl + "/termos#t=" + token;
    }

    private static long currentVersion(TermsConsentSession session) {
        return session.version();
    }

    private static String validateBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("terms public base URL must not be blank");
        }
        URI uri = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("terms public base URL must be an HTTPS origin");
        }
        String normalized = value.trim().replaceFirst("/+$", "");
        return normalized;
    }

    public enum Decision {
        ACCEPT {
            @Override TermsConsentDecision asDomain() { return TermsConsentDecision.ACCEPT; }
        },
        DECLINE {
            @Override TermsConsentDecision asDomain() { return TermsConsentDecision.DECLINE; }
        };

        abstract TermsConsentDecision asDomain();
    }

    public record IssueRequest(String conversationId, String contactId, String destinationRef,
                               String contractingUnitId, String environment, String serviceType,
                               String termsVersion, String termsResource, String termsContent,
                               String correlationId) {
        public IssueRequest {
            require(conversationId, "conversationId");
            require(contactId, "contactId");
            require(destinationRef, "destinationRef");
            require(contractingUnitId, "contractingUnitId");
            require(environment, "environment");
            require(serviceType, "serviceType");
            require(termsResource, "termsResource");
            require(correlationId, "correlationId");
        }
    }

    public record Issued(String presentationId, String token, String url, TermsConsentSession session) { }

    public record Presentation(String presentationId, String serviceType, String termsVersion,
                               String termsHash, String termsResource, String content,
                               TermsConsentSessionStatus status, TermsConsentDecision decision) { }

    public record SessionResult(TermsConsentSession session, boolean idempotent) { }

    public record DecisionResult(String presentationId, TermsConsentSessionStatus status,
                                 TermsConsentDecision decision, boolean idempotent,
                                 String continuationMessage) { }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() { super("terms token is invalid"); }
    }

    public static class ExpiredSessionException extends RuntimeException {
        public ExpiredSessionException() { super("terms session has expired"); }
    }

    public static class DecisionConflictException extends RuntimeException {
        public DecisionConflictException(String message) { super(message == null ? "terms decision is not allowed" : message); }
    }

    public static class BindingMismatchException extends RuntimeException {
        public BindingMismatchException(String message) { super(message); }
    }

    public static class TermsContentUnavailableException extends RuntimeException {
        public TermsContentUnavailableException() { super("approved terms content is unavailable"); }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
