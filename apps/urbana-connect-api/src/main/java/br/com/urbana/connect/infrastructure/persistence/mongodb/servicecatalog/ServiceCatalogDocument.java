package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.model.AreaRule;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.util.List;

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
    private String termsResource;
    private String paymentResource;
    private String briefingResource;
    private AreaRule areaRule;
    private String scope;
    private List<String> deliverables;
    private List<String> process;
    private List<String> responsibilities;
    private List<String> exclusions;
    private String support;
    private boolean available;

    /** Compatibility aliases for the previous persistence adapter contract. */
    public String getPaymentLink() {
        return paymentResource;
    }

    public void setPaymentLink(String paymentLink) {
        this.paymentResource = paymentLink;
    }

    public String getBriefingLink() {
        return briefingResource;
    }

    public void setBriefingLink(String briefingLink) {
        this.briefingResource = briefingLink;
    }
}
