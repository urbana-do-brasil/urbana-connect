package br.com.urbana.connect.domain.reception.port.out;

import java.util.Optional;

/**
 * Replaces the local speech-to-text implementation without leaking provider
 * details into the reception use cases.
 */
@FunctionalInterface
public interface TranscriptionGateway {
    Optional<String> transcribe(String mediaReference);
}
