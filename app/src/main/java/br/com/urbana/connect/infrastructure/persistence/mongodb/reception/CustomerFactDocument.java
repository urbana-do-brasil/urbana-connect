package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.FactConfidence;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reception_customer_facts")
public class CustomerFactDocument {
    @Id
    private String id;
    @Indexed
    private String contactId;
    @Indexed
    private String type;
    private String value;
    private FactConfidence confidence;
    private String sourceMessageId;
    private Instant validFrom;
    private Instant validUntil;
    private String supersededBy;
}
