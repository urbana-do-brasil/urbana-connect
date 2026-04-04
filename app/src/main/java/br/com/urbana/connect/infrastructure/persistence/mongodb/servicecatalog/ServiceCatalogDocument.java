package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;

@Data
@Document(collection = "services")
public class ServiceCatalogDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private ServiceType type;
    private String name;
    private String emoji;
    private String scenarioText;
    private String presentationText;
    private BigDecimal price;
    private String paymentLink;
    private String briefingLink;
    private boolean available;
}
