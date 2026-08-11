package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.port.out.TranscriptionGateway;

import java.util.Objects;
import java.util.Optional;

/** Normalizes channel media into the canonical input consumed by Hermes. */
public final class MediaNormalizationService {
    private final TranscriptionGateway transcription;

    public MediaNormalizationService() {
        this(new br.com.urbana.connect.infrastructure.media.LocalWhisperTranscriptionGateway());
    }

    public MediaNormalizationService(TranscriptionGateway transcription) {
        this.transcription = Objects.requireNonNull(transcription, "transcription");
    }

    public NormalizedMedia normalize(InboundConversationEvent event) {
        Objects.requireNonNull(event, "event");
        String text = event.conversationalText();
        if (event.type() == br.com.urbana.connect.domain.reception.model.ReceptionMessageType.AUDIO
                && (text == null || text.isBlank())) {
            text = transcription.transcribe(event.mediaFixture()).orElse("");
        }

        boolean paymentProof = event.isPaymentProof();
        Optional<String> inlineImage = isInlineImage(event.mediaFixture())
                ? Optional.of(event.mediaFixture()) : Optional.empty();
        return new NormalizedMedia(
                text == null ? "" : text,
                event.mediaFixture(),
                MediaKind.from(event.type()),
                paymentProof,
                false,
                paymentProof ? "PROOF_RECEIVED" : null,
                inlineImage);
    }

    /**
     * Returns the same canonical event with an available audio transcript in
     * the transcript field. The original media reference is always retained.
     */
    public InboundConversationEvent normalizeEvent(InboundConversationEvent event) {
        Objects.requireNonNull(event, "event");
        NormalizedMedia normalized = normalize(event);
        String transcript = event.transcript();
        if (event.type() == br.com.urbana.connect.domain.reception.model.ReceptionMessageType.AUDIO
                && !normalized.text().isBlank()) {
            transcript = normalized.text();
        }
        return new InboundConversationEvent(event.eventId(), event.contactId(), event.type(), event.text(),
                transcript, event.mediaFixture(), event.interactiveReplyId(), event.occurredAt(),
                event.providerMessageId());
    }

    private static boolean isInlineImage(String reference) {
        return reference != null && reference.regionMatches(true, 0, "data:image/", 0, 11);
    }

    public enum MediaKind {
        TEXT, AUDIO, IMAGE, DOCUMENT, INTERACTIVE, PAYMENT_PROOF;

        static MediaKind from(br.com.urbana.connect.domain.reception.model.ReceptionMessageType type) {
            return switch (type) {
                case TEXT -> TEXT;
                case AUDIO -> AUDIO;
                case IMAGE -> IMAGE;
                case DOCUMENT -> DOCUMENT;
                case INTERACTIVE -> INTERACTIVE;
                case PAYMENT_PROOF -> PAYMENT_PROOF;
            };
        }
    }

    public record NormalizedMedia(
            String text,
            String mediaReference,
            MediaKind kind,
            boolean paymentProof,
            boolean paymentApprovalAllowed,
            String paymentEvidenceStatus,
            Optional<String> inlineImage) {
        public NormalizedMedia {
            text = text == null ? "" : text;
            kind = Objects.requireNonNull(kind, "kind");
            inlineImage = inlineImage == null ? Optional.empty() : inlineImage;
            if (paymentApprovalAllowed) {
                throw new IllegalArgumentException("media normalization cannot approve payment");
            }
            if (paymentProof && !"PROOF_RECEIVED".equals(paymentEvidenceStatus)) {
                throw new IllegalArgumentException("payment proof must remain PROOF_RECEIVED evidence");
            }
        }
    }
}
