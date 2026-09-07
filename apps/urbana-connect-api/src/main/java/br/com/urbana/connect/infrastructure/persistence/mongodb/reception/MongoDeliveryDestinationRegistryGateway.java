package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationRegistryGateway;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;
import java.util.regex.Pattern;

/** Mongo-backed, first-write-wins channel-address registry. */
public final class MongoDeliveryDestinationRegistryGateway implements DeliveryDestinationRegistryGateway {
    private static final Pattern CONTACT_REF = Pattern.compile("[A-Za-z0-9:_-]{1,256}");
    private static final Pattern WHATSAPP_ADDRESS = Pattern.compile("\\+?[0-9]{7,32}");

    private final SpringDataDeliveryDestinationRepository repository;

    public MongoDeliveryDestinationRegistryGateway(SpringDataDeliveryDestinationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveIfAbsent(String contactRef, String channelAddress) {
        String normalizedContact = require(contactRef, CONTACT_REF, "contactRef");
        String normalizedAddress = require(normalizeAddress(channelAddress), WHATSAPP_ADDRESS, "channelAddress");
        if (repository.findById(normalizedContact).isPresent()) {
            return;
        }
        DeliveryDestinationDocument document = new DeliveryDestinationDocument();
        document.setContactRef(normalizedContact);
        document.setChannelAddress(normalizedAddress);
        document.setCreatedAt(java.time.Instant.now());
        try {
            repository.insert(document);
        } catch (DuplicateKeyException race) {
            // A concurrent webhook for the same contact is idempotent. A
            // pre-existing address is never overwritten by an inbound retry.
            if (repository.findById(normalizedContact).isEmpty()) {
                throw new IllegalStateException("delivery destination lost during idempotent registration", race);
            }
        }
    }

    @Override
    public Optional<String> find(String contactRef) {
        if (contactRef == null || !CONTACT_REF.matcher(contactRef.trim()).matches()) {
            return Optional.empty();
        }
        return repository.findById(contactRef.trim())
                .map(DeliveryDestinationDocument::getChannelAddress)
                .map(MongoDeliveryDestinationRegistryGateway::normalizeAddress)
                .filter(value -> value != null && WHATSAPP_ADDRESS.matcher(value).matches());
    }

    private static String normalizeAddress(String value) {
        if (value == null) return null;
        String trimmed = value.trim().replaceAll("[\\s()-]", "");
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String require(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }
}
