package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

public class MongoReceptionConversationGateway implements ReceptionConversationGateway {
    private final SpringDataReceptionConversationRepository repository;
    private final MongoTemplate template;

    public MongoReceptionConversationGateway(SpringDataReceptionConversationRepository repository) {
        this(repository, null);
    }

    public MongoReceptionConversationGateway(SpringDataReceptionConversationRepository repository,
                                              MongoTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public Optional<ReceptionConversation> findByContactId(String contactId) {
        return repository.findByContactId(contactId).map(this::toDomain);
    }

    @Override
    public ReceptionConversation save(ReceptionConversation conversation) {
        long expectedVersion = conversation.version() == 0 ? -1 : conversation.version() - 1;
        return saveExpected(conversation, expectedVersion);
    }

    /**
     * Persists a transition only when the caller read the expected version.
     * The repository fallback is retained for isolated unit fakes; production
     * wiring supplies MongoTemplate and therefore uses one atomic CAS update.
     */
    public ReceptionConversation saveExpected(ReceptionConversation conversation, long expectedVersion) {
        if (template == null || conversation.version() == 0) {
            return toDomain(repository.save(toDocument(conversation)));
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(conversation.id()),
                Criteria.where("version").is(expectedVersion)));
        Update update = new Update()
                .set("contactId", conversation.contactId())
                .set("mode", conversation.mode())
                .set("commercialStage", conversation.commercialStage())
                .set("selectedService", conversation.selectedService())
                .set("termsStatus", conversation.termsStatus())
                .set("paymentStatus", conversation.paymentStatus())
                .set("handoffReason", conversation.handoffReason())
                .set("createdAt", conversation.createdAt())
                .set("updatedAt", conversation.updatedAt())
                .set("version", conversation.version());
        ReceptionConversationDocument updated = template.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ReceptionConversationDocument.class);
        if (updated == null) {
            throw new IllegalStateException("reception conversation changed concurrently");
        }
        return toDomain(updated);
    }

    private ReceptionConversationDocument toDocument(ReceptionConversation value) {
        ReceptionConversationDocument d = new ReceptionConversationDocument();
        d.setId(value.id()); d.setContactId(value.contactId()); d.setMode(value.mode());
        d.setCommercialStage(value.commercialStage()); d.setSelectedService(value.selectedService());
        d.setTermsStatus(value.termsStatus()); d.setPaymentStatus(value.paymentStatus());
        d.setHandoffReason(value.handoffReason()); d.setCreatedAt(value.createdAt());
        d.setUpdatedAt(value.updatedAt()); d.setVersion(value.version());
        return d;
    }

    private ReceptionConversation toDomain(ReceptionConversationDocument d) {
        return new ReceptionConversation(d.getId(), d.getContactId(), d.getMode(), d.getCommercialStage(),
                d.getSelectedService(), d.getTermsStatus(), d.getPaymentStatus(), d.getHandoffReason(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
    }
}
