package br.com.urbana.connect.domain.reception.port.out;

import java.util.Optional;

/**
 * Protected channel-address registry keyed by the opaque contact reference.
 * The registry is the only boundary allowed to resolve a WhatsApp address;
 * callers must never derive a phone number by reversing the contact hash.
 */
public interface DeliveryDestinationRegistryGateway {
    void saveIfAbsent(String contactRef, String channelAddress);

    Optional<String> find(String contactRef);
}
