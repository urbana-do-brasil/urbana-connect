package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataDeliveryDestinationRepository
        extends MongoRepository<DeliveryDestinationDocument, String> {
}
