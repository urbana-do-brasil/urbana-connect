package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoDeliveryDestinationRegistryGatewayTest {
    @Test
    void registersAnOpaqueContactOnlyOnceAndNeverOverwritesItsAddress() {
        SpringDataDeliveryDestinationRepository repository = mock(SpringDataDeliveryDestinationRepository.class);
        when(repository.findById("wa:opaque-contact"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(document("5511999999999")));
        MongoDeliveryDestinationRegistryGateway gateway =
                new MongoDeliveryDestinationRegistryGateway(repository);

        gateway.saveIfAbsent("wa:opaque-contact", "+55 (11) 99999-9999");
        gateway.saveIfAbsent("wa:opaque-contact", "5511888888888");

        verify(repository).insert(any(DeliveryDestinationDocument.class));
        verify(repository, never()).save(any(DeliveryDestinationDocument.class));
        assertThat(gateway.find("wa:opaque-contact")).contains("5511999999999");
    }

    @Test
    void rejectsInvalidChannelAddressesInsteadOfPersistingThem() {
        SpringDataDeliveryDestinationRepository repository = mock(SpringDataDeliveryDestinationRepository.class);
        MongoDeliveryDestinationRegistryGateway gateway =
                new MongoDeliveryDestinationRegistryGateway(repository);

        assertThatThrownBy(() -> gateway.saveIfAbsent("wa:opaque-contact", "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channelAddress");
        verify(repository, never()).insert(any(DeliveryDestinationDocument.class));
    }

    private static DeliveryDestinationDocument document(String address) {
        DeliveryDestinationDocument document = new DeliveryDestinationDocument();
        document.setContactRef("wa:opaque-contact");
        document.setChannelAddress(address);
        return document;
    }
}
