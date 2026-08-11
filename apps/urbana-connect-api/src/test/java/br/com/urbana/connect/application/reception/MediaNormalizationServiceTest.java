package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.TranscriptionGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MediaNormalizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void usesAvailableAudioTranscriptAndPreservesOriginalReference() {
        TranscriptionGateway gateway = media -> Optional.of("Quero decorar a sala");
        MediaNormalizationService service = new MediaNormalizationService(gateway);

        MediaNormalizationService.NormalizedMedia result = service.normalize(new InboundConversationEvent(
                "audio-1", "poc:ana", ReceptionMessageType.AUDIO, null, null,
                "poc/audio-room.txt", null, NOW, null));

        assertThat(result.text()).isEqualTo("Quero decorar a sala");
        assertThat(result.mediaReference()).isEqualTo("poc/audio-room.txt");
        assertThat(result.kind()).isEqualTo(MediaNormalizationService.MediaKind.AUDIO);
        assertThat(result.paymentProof()).isFalse();
    }

    @Test
    void prefersCanonicalTranscriptWhenChannelAlreadyProvidedIt() {
        MediaNormalizationService service = new MediaNormalizationService(media -> {
            throw new AssertionError("transcription must not be called");
        });

        MediaNormalizationService.NormalizedMedia result = service.normalize(new InboundConversationEvent(
                "audio-2", "poc:ana", ReceptionMessageType.AUDIO, null,
                "Texto transcrito pelo canal", "poc/audio-room.txt", null, NOW, null));

        assertThat(result.text()).isEqualTo("Texto transcrito pelo canal");
    }

    @Test
    void keepsEnvironmentImageInlineButNeverTreatsItAsPaymentApproval() {
        MediaNormalizationService service = new MediaNormalizationService(media -> Optional.empty());
        String inlineImage = "data:image/png;base64,ZmFrZQ==";

        MediaNormalizationService.NormalizedMedia result = service.normalize(new InboundConversationEvent(
                "image-1", "poc:ana", ReceptionMessageType.IMAGE, null, null,
                inlineImage, null, NOW, null));

        assertThat(result.kind()).isEqualTo(MediaNormalizationService.MediaKind.IMAGE);
        assertThat(result.inlineImage()).contains(inlineImage);
        assertThat(result.paymentProof()).isFalse();
        assertThat(result.paymentApprovalAllowed()).isFalse();
    }

    @Test
    void marksProofAsReceivedEvidenceOnly() {
        MediaNormalizationService service = new MediaNormalizationService(media -> Optional.empty());

        MediaNormalizationService.NormalizedMedia result = service.normalize(new InboundConversationEvent(
                "proof-1", "poc:ana", ReceptionMessageType.PAYMENT_PROOF, null, null,
                "poc/payment-proof-fixture.svg", null, NOW, null));

        assertThat(result.paymentProof()).isTrue();
        assertThat(result.paymentApprovalAllowed()).isFalse();
        assertThat(result.paymentEvidenceStatus()).isEqualTo("PROOF_RECEIVED");
    }

    @Test
    void preservesTextAndDocumentReferenceForNonAudioMedia() {
        MediaNormalizationService service = new MediaNormalizationService(media -> Optional.empty());

        MediaNormalizationService.NormalizedMedia result = service.normalize(new InboundConversationEvent(
                "doc-1", "poc:ana", ReceptionMessageType.DOCUMENT, "segue o arquivo", null,
                "poc/briefing-reference.txt", null, NOW, null));

        assertThat(result.text()).isEqualTo("segue o arquivo");
        assertThat(result.mediaReference()).isEqualTo("poc/briefing-reference.txt");
        assertThat(result.kind()).isEqualTo(MediaNormalizationService.MediaKind.DOCUMENT);
    }
}
