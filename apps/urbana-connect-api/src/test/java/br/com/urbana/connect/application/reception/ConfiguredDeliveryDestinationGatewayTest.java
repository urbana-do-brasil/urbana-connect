package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationRegistryGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredDeliveryDestinationGatewayTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void resolvesMailboxAndConversationOnlyFromExplicitConfiguration() {
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        ReceptionConversation conversation = ReceptionConversation.start("conv-1", "wa:opaque-contact", NOW);
        when(conversations.findById("conv-1")).thenReturn(Optional.of(conversation));
        DeliveryDestinationProperties properties = new DeliveryDestinationProperties();
        properties.setSupportMailbox("hml-atendimento@example.test");
        properties.setContactAddresses(Map.of("wa:opaque-contact", "5511999999999"));
        ConfiguredDeliveryDestinationGateway gateway = new ConfiguredDeliveryDestinationGateway(conversations, properties);

        assertThat(gateway.resolve(intent("support-mailbox"))).contains("hml-atendimento@example.test");
        assertThat(gateway.resolve(intent("conversation:conv-1"))).contains("5511999999999");
        assertThat(gateway.resolve(intent("contact:unknown"))).isEmpty();
    }

    @Test
    void rejectsEnabledDeliveryWithoutAnExplicitMailbox() {
        ConfiguredDeliveryDestinationGateway gateway = new ConfiguredDeliveryDestinationGateway(
                mock(ReceptionConversationGateway.class), new DeliveryDestinationProperties());

        org.assertj.core.api.Assertions.assertThatThrownBy(gateway::requireOperationalConfiguration)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("support-mailbox");
    }

    @Test
    void resolvesOpaqueContactFromTheProtectedRegistryWithoutReversingIt() {
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        DeliveryDestinationRegistryGateway registry = mock(DeliveryDestinationRegistryGateway.class);
        when(registry.find("wa:opaque-contact")).thenReturn(Optional.of("5511888888888"));
        DeliveryDestinationProperties properties = new DeliveryDestinationProperties();
        properties.setSupportMailbox("hml-atendimento@example.test");
        ConfiguredDeliveryDestinationGateway gateway = new ConfiguredDeliveryDestinationGateway(
                conversations, registry, properties);

        assertThat(gateway.resolve(intent("contact:wa:opaque-contact"))).contains("5511888888888");
        assertThat(gateway.resolve(intent("contact:wa:unknown"))).isEmpty();
        org.assertj.core.api.Assertions.assertThatCode(gateway::requireOperationalConfiguration)
                .doesNotThrowAnyException();
    }

    private static DeliveryOutbox intent(String destinationRef) {
        return DeliveryOutbox.pending("event-" + destinationRef, DeliveryOutboxKind.WHATSAPP_MENU,
                destinationRef, "payload", "corr", NOW);
    }
}
