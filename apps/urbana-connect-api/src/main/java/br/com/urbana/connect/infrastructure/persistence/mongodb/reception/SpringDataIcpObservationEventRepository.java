package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataIcpObservationEventRepository
        extends MongoRepository<IcpObservationEventDocument, String> {
}
