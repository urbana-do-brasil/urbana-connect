package br.com.urbana.connect.infrastructure.media;

import br.com.urbana.connect.domain.reception.port.out.TranscriptionGateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Small local adapter for an optionally installed Whisper CLI. The POC keeps
 * it substitutable and returns no transcript when the optional binary is not
 * configured, preserving the original media reference for later handling.
 */
public final class LocalWhisperTranscriptionGateway implements TranscriptionGateway {
    private final String executable;
    private final Duration timeout;

    public LocalWhisperTranscriptionGateway() {
        this(System.getenv().getOrDefault("WHISPER_EXECUTABLE", "whisper"), Duration.ofSeconds(30));
    }

    public LocalWhisperTranscriptionGateway(String executable, Duration timeout) {
        this.executable = executable == null ? "" : executable.trim();
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        if (this.timeout.isNegative() || this.timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @Override
    public Optional<String> transcribe(String mediaReference) {
        if (mediaReference == null || mediaReference.isBlank() || executable.isBlank()) {
            return Optional.empty();
        }
        Process process;
        try {
            process = new ProcessBuilder(List.of(executable, mediaReference))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException | RuntimeException unavailable) {
            return Optional.empty();
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && !output.isBlank() ? Optional.of(output) : Optional.empty();
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalThreadStateException failure) {
            process.destroyForcibly();
            return Optional.empty();
        }
    }
}
