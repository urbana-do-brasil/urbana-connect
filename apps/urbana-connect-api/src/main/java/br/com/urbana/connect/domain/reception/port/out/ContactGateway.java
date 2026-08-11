package br.com.urbana.connect.domain.reception.port.out;

import java.util.Optional;

/** Resolves an already authenticated boundary identity to an opaque contact id. */
public interface ContactGateway {
    Optional<String> findContactIdByChannelAddress(String normalizedAddress);

    String getOrCreateContactId(String normalizedAddress);
}
