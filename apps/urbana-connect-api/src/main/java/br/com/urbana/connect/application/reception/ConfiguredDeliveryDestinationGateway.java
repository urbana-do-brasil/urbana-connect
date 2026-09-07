package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationRegistryGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves only explicitly configured addresses; hashes are never reversible. */
public final class ConfiguredDeliveryDestinationGateway implements DeliveryDestinationGateway {
    private final ReceptionConversationGateway conversations;
    private final DeliveryDestinationRegistryGateway registry;
    private final String supportMailbox;
    private final Map<String, String> contactAddresses;

    public ConfiguredDeliveryDestinationGateway(ReceptionConversationGateway conversations,
                                                DeliveryDestinationProperties properties) {
        this(conversations, null, properties);
    }

    public ConfiguredDeliveryDestinationGateway(ReceptionConversationGateway conversations,
                                                DeliveryDestinationRegistryGateway registry,
                                                DeliveryDestinationProperties properties) {
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.registry = registry;
        Objects.requireNonNull(properties, "properties");
        this.supportMailbox = normalize(properties.getSupportMailbox());
        this.contactAddresses = properties.getContactAddresses().entrySet().stream()
                .filter(entry -> normalize(entry.getKey()) != null && normalize(entry.getValue()) != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> normalize(entry.getKey()), entry -> normalize(entry.getValue()), (a, ignored) -> a));
    }

    @Override
    public Optional<String> resolve(DeliveryOutbox outbox) {
        if (outbox == null) return Optional.empty();
        String reference = outbox.destinationRef();
        if ("support-mailbox".equals(reference)) return Optional.ofNullable(supportMailbox);
        if (reference.startsWith("contact:")) {
            return resolveContact(reference.substring("contact:".length()));
        }
        if (reference.startsWith("conversation:")) {
            String id = normalize(reference.substring("conversation:".length()));
            if (id == null) return Optional.empty();
            return conversations.findById(id)
                    .flatMap(conversation -> resolveContact(conversation.contactId()));
        }
        return Optional.empty();
    }

    public void requireOperationalConfiguration() {
        if (supportMailbox == null) {
            throw new IllegalStateException("delivery.outbox.destinations.support-mailbox is required when delivery is enabled");
        }
        if (registry == null) {
            throw new IllegalStateException("a durable delivery destination registry is required when delivery is enabled");
        }
    }

    private Optional<String> resolveContact(String contactRef) {
        String normalized = normalize(contactRef);
        if (normalized == null) return Optional.empty();
        String configured = contactAddresses.get(normalized);
        if (configured != null) return Optional.of(configured);
        return registry == null ? Optional.empty() : registry.find(normalized);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
