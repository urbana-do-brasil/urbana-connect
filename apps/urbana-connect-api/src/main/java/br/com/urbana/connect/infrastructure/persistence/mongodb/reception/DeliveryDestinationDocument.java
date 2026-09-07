package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Internal channel-address projection. Mongo encryption-at-rest and access
 * controls remain operational requirements; this document is never returned
 * by a public endpoint or copied into a delivery payload.
 */
@Data
@Document(collection = "reception_delivery_destinations")
public class DeliveryDestinationDocument {
    @Id
    private String contactRef;
    @Indexed(unique = true)
    private String channelAddress;
    private Instant createdAt;
}
