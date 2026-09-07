package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.reception.tools.DomainToolInvocationUseCase;
import br.com.urbana.connect.application.reception.tools.DomainToolService;
import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.PocPendingEventGateway;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditEventGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentSessionGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsContentGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationRegistryGateway;
import br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoActiveTurnLeaseGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoAgentSessionLinkGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoDomainToolInvocationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoCustomerFactGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoReceptionConversationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoReceptionTranscriptGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoPocPendingEventGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataActiveTurnLeaseRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataAgentSessionLinkRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataDomainToolInvocationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataCustomerFactRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionConversationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionMessageRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionTurnRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataTermsConsentAuditRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoTermsConsentAuditGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataTermsConsentSessionRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoTermsConsentSessionGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataTermsConsentAuditEventRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoTermsConsentAuditEventGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataDeliveryOutboxRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoDeliveryOutboxGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataDeliveryDestinationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoDeliveryDestinationRegistryGateway;
import br.com.urbana.connect.infrastructure.delivery.DeliveryChannelRouter;
import br.com.urbana.connect.infrastructure.mail.SmtpDeliveryChannelGateway;
import br.com.urbana.connect.infrastructure.whatsapp.WhatsAppDeliveryChannelGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataPocPendingEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/** Hermes reception wiring shared by the local simulator and the WhatsApp POC route. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(DeliveryDestinationProperties.class)
@ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
public class PocReceptionConfiguration {

    @Bean
    public AgentSessionLinkGateway agentSessionLinkGateway(SpringDataAgentSessionLinkRepository repository,
                                                           MongoTemplate template) {
        return new MongoAgentSessionLinkGateway(repository, template);
    }

    @Bean
    public ActiveTurnLeaseGateway activeTurnLeaseGateway(SpringDataActiveTurnLeaseRepository repository,
                                                        MongoTemplate template) {
        return new MongoActiveTurnLeaseGateway(repository, template);
    }

    @Bean
    public DomainToolInvocationGateway domainToolInvocationGateway(
            SpringDataDomainToolInvocationRepository repository) {
        return new MongoDomainToolInvocationGateway(repository);
    }

    @Bean
    public DomainToolService statefulDomainToolService(CommercialPolicyService policy,
                                                       ReceptionConversationGateway conversations,
                                                       CustomerFactGateway facts,
                                                       ReceptionTranscriptGateway transcript,
                                                       TermsAcceptanceUseCase termsAcceptance,
                                                       ObjectProvider<TermsConsentService> webTermsConsent,
                                                       @Value("${terms.consent.required:true}") boolean webTermsRequired) {
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);
        tools.setTermsAcceptanceUseCase(termsAcceptance);
        webTermsConsent.ifAvailable(tools::setTermsConsentService);
        tools.setWebTermsRequired(webTermsRequired);
        return tools;
    }

    @Bean
    public ActiveTurnLeaseService activeTurnLeaseService(ActiveTurnLeaseGateway gateway,
                                                         @Value("${hermes.sessions.lease-ttl:240s}") String ttl) {
        return new ActiveTurnLeaseService(gateway, Clock.systemUTC(), DurationStyle.detectAndParse(ttl));
    }

    @Bean
    public DomainToolInvocationUseCase domainToolInvocationUseCase(
            ActiveTurnLeaseService leases, DomainToolInvocationGateway invocations,
            DomainToolService tools, ReceptionConversationGateway conversations, ReceptionMetrics metrics) {
        return new DomainToolInvocationUseCase(leases, invocations, tools, Clock.systemUTC(), conversations, metrics);
    }

    @Bean
    public ReceptionConversationGateway receptionConversationGateway(
            SpringDataReceptionConversationRepository repository, MongoTemplate template) {
        return new MongoReceptionConversationGateway(repository, template);
    }

    @Bean
    public TermsConsentAuditGateway termsConsentAuditGateway(
            SpringDataTermsConsentAuditRepository repository, MongoTemplate template) {
        return new MongoTermsConsentAuditGateway(repository, template);
    }

    /** Web-consent persistence is opt-in until the approved legal artifacts and secret are configured. */
    @Bean
    @ConditionalOnProperty(name = "terms.consent.enabled", havingValue = "true")
    public TermsConsentSessionGateway termsConsentSessionGateway(
            SpringDataTermsConsentSessionRepository repository, MongoTemplate template) {
        return new MongoTermsConsentSessionGateway(repository, template);
    }

    @Bean
    @ConditionalOnProperty(name = "terms.consent.enabled", havingValue = "true")
    public TermsConsentAuditEventGateway termsConsentAuditEventGateway(
            SpringDataTermsConsentAuditEventRepository repository) {
        return new MongoTermsConsentAuditEventGateway(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
    public DeliveryOutboxGateway deliveryOutboxGateway(SpringDataDeliveryOutboxRepository repository) {
        return new MongoDeliveryOutboxGateway(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
    public DeliveryDestinationRegistryGateway deliveryDestinationRegistryGateway(
            SpringDataDeliveryDestinationRepository repository) {
        return new MongoDeliveryDestinationRegistryGateway(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
    public HermesFailureHandoffService hermesFailureHandoffService(DeliveryOutboxGateway outbox,
                                                                    ReceptionConversationGateway conversations) {
        return new HermesFailureHandoffService(outbox, conversations, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
    public HermesOutboundPublisher hermesOutboundPublisher(DeliveryOutboxGateway outbox) {
        return new HermesOutboundPublisher(outbox, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.outbox.enabled", havingValue = "true")
    public DeliveryDestinationGateway deliveryDestinationGateway(
            ReceptionConversationGateway conversations,
            DeliveryDestinationRegistryGateway registry,
            DeliveryDestinationProperties properties) {
        ConfiguredDeliveryDestinationGateway gateway = new ConfiguredDeliveryDestinationGateway(
                conversations, registry, properties);
        gateway.requireOperationalConfiguration();
        return gateway;
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.outbox.enabled", havingValue = "true")
    public DeliveryChannelGateway deliveryChannelGateway(
            WhatsAppMessageGateway whatsapp,
            JavaMailSender mailSender,
            @Value("${whatsapp.api.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.api.access-token:}") String accessToken,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailFrom) {
        requireDeliverySetting(phoneNumberId, "whatsapp.api.phone-number-id");
        requireDeliverySetting(accessToken, "whatsapp.api.access-token");
        requireDeliverySetting(mailHost, "spring.mail.host");
        DeliveryChannelGateway whatsappChannel = new WhatsAppDeliveryChannelGateway(whatsapp);
        DeliveryChannelGateway smtpChannel = new SmtpDeliveryChannelGateway(mailSender, mailFrom,
                "Urba Connect - atendimento humano solicitado");
        return new DeliveryChannelRouter(Map.of(
                br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind.WHATSAPP_TERMS_LINK, whatsappChannel,
                br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind.WHATSAPP_PAYMENT_OPTIONS, whatsappChannel,
                br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind.WHATSAPP_MENU, whatsappChannel,
                br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind.WHATSAPP_HERMES_REPLY, whatsappChannel,
                br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind.WHATSAPP_HUMAN_HANDOFF_ACK, whatsappChannel,
                br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF, smtpChannel));
    }

    /**
     * Delivery is enabled only after the channel/provider discovery supplies
     * both a protected destination resolver and concrete channel adapters.
     * Leaving the property false keeps HML fail-closed without inventing a
     * recipient or a payment provider.
     */
    @Bean
    @ConditionalOnProperty(name = "delivery.outbox.enabled", havingValue = "true")
    public DeliveryOutboxWorker deliveryOutboxWorker(
            DeliveryOutboxGateway outbox,
            DeliveryDestinationGateway destinations,
            DeliveryChannelGateway channel,
            @Value("${delivery.outbox.retry-base-delay:10s}") String retryBaseDelay,
            @Value("${delivery.outbox.recovery-lease:5m}") String recoveryLease,
            @Value("${delivery.outbox.max-attempts:5}") int maxAttempts,
            @Value("${delivery.outbox.batch-size:50}") int batchSize) {
        return new DeliveryOutboxWorker(outbox, destinations, channel, Clock.systemUTC(),
                DurationStyle.detectAndParse(retryBaseDelay), DurationStyle.detectAndParse(recoveryLease),
                maxAttempts, batchSize);
    }

    @Bean
    @ConditionalOnProperty(name = "terms.consent.enabled", havingValue = "true")
    public TermsContentGateway termsContentGateway(
            @Value("${terms.documents.decor-interiores.version:}") String interioresVersion,
            @Value("${terms.documents.decor-interiores.resource:}") String interioresResource,
            @Value("${terms.documents.decor-interiores.content:}") String interioresContent,
            @Value("${terms.documents.decor-pintura.version:}") String pinturaVersion,
            @Value("${terms.documents.decor-pintura.resource:}") String pinturaResource,
            @Value("${terms.documents.decor-pintura.content:}") String pinturaContent,
            @Value("${terms.documents.decor-fachada.version:}") String fachadaVersion,
            @Value("${terms.documents.decor-fachada.resource:}") String fachadaResource,
            @Value("${terms.documents.decor-fachada.content:}") String fachadaContent,
            @Value("${terms.documents.decor-reforma.version:}") String reformaVersion,
            @Value("${terms.documents.decor-reforma.resource:}") String reformaResource,
            @Value("${terms.documents.decor-reforma.content:}") String reformaContent) {
        Map<String, TermsContentGateway.TermsContent> documents = new java.util.HashMap<>();
        configuredTerms(documents, "DECOR_INTERIORES", interioresVersion, interioresResource, interioresContent);
        configuredTerms(documents, "DECOR_PINTURA", pinturaVersion, pinturaResource, pinturaContent);
        configuredTerms(documents, "DECOR_FACHADA", fachadaVersion, fachadaResource, fachadaContent);
        configuredTerms(documents, "DECOR_REFORMA", reformaVersion, reformaResource, reformaContent);
        return new ConfiguredTermsContentGateway(documents);
    }

    @Bean
    @ConditionalOnProperty(name = "terms.consent.enabled", havingValue = "true")
    public TermsConsentService termsConsentService(
            TermsConsentSessionGateway sessions,
            TermsConsentAuditEventGateway auditEvents,
            DeliveryOutboxGateway outbox,
            ReceptionConversationGateway conversations,
            TermsContentGateway contentGateway,
            @Value("${terms.consent.public-base-url:https://api-hml.urbanadobrasil.com}") String publicBaseUrl,
            @Value("${terms.consent.token-secret:}") String tokenSecret,
            @Value("${terms.consent.ttl:30m}") String ttl) {
        return new TermsConsentService(sessions, auditEvents, outbox, conversations, contentGateway,
                Clock.systemUTC(), publicBaseUrl, tokenSecret, DurationStyle.detectAndParse(ttl));
    }

    @Bean
    public TermsAcceptanceUseCase termsAcceptanceUseCase(TermsConsentAuditGateway audits,
                                                         ReceptionConversationGateway conversations) {
        return new TermsAcceptanceUseCase(audits, conversations);
    }

    @Bean
    public CustomerFactGateway customerFactGateway(SpringDataCustomerFactRepository repository) {
        return new MongoCustomerFactGateway(repository);
    }

    @Bean
    public ReturningCustomerService returningCustomerService(CustomerFactGateway facts,
                                                             CommercialPolicyService policy) {
        return new ReturningCustomerService(facts, policy, Clock.systemUTC());
    }

    @Bean
    public ReceptionTranscriptGateway receptionTranscriptGateway(SpringDataReceptionMessageRepository repository) {
        return new MongoReceptionTranscriptGateway(repository);
    }

    @Bean
    public ReceptionTurnGateway receptionTurnGateway(SpringDataReceptionTurnRepository repository) {
        return new MongoReceptionTurnGateway(repository);
    }

    @Bean
    public PocPendingEventGateway pocPendingEventGateway(SpringDataPocPendingEventRepository repository,
                                                         MongoTemplate template) {
        return new MongoPocPendingEventGateway(repository, template);
    }

    @Bean
    public ReceptionTurnCoordinator receptionTurnCoordinator(ReceptionTranscriptGateway transcript,
                                                             ReceptionTurnGateway turns) {
        return new ReceptionTurnCoordinator(transcript, turns);
    }

    @Bean
    public CommercialPolicyService commercialPolicyService(ServiceCatalogGateway serviceCatalogGateway) {
        return new CommercialPolicyService(serviceCatalogGateway);
    }

    @Bean
    public NonProspectPolicy nonProspectPolicy() {
        return new NonProspectPolicy();
    }

    @Bean
    public ReceptionMetrics receptionMetrics() {
        return new ReceptionMetrics();
    }

    @Bean
    public MessageBatcher messageBatcher() {
        return new MessageBatcher();
    }

    @Bean
    public MediaNormalizationService mediaNormalizationService() {
        return new MediaNormalizationService();
    }

    @Bean
    public HermesSessionsGateway hermesSessionsGateway(
            RestClient.Builder builder,
            @Value("${hermes.sessions.base-url:http://127.0.0.1:8642}") String baseUrl,
            @Value("${hermes.sessions.api-server-key:}") String apiKey,
            @Value("${hermes.sessions.model:openai/gpt-5.6-luna}") String model,
            @Value("${hermes.sessions.reasoning-effort:max}") String reasoningEffort,
            @Value("${hermes.sessions.timeout:180s}") String timeout) {
        return new HttpHermesSessionsGateway(builder, baseUrl, apiKey, model, reasoningEffort,
                DurationStyle.detectAndParse(timeout));
    }

    @Bean
    public HermesSessionService hermesSessionService(HermesSessionsGateway sessions,
                                                     AgentSessionLinkGateway links) {
        return new HermesSessionService(sessions, links);
    }

    @Bean
    public ReceptionTurnReconciliationService receptionTurnReconciliationService(
            HermesSessionService hermes, ReceptionConversationGateway conversations,
            ReceptionTranscriptGateway transcript, ReceptionTurnGateway turns,
            ActiveTurnLeaseService leases, CommercialPolicyService policy,
            TermsAcceptanceUseCase termsAcceptance, DomainToolInvocationGateway invocations) {
        return new ReceptionTurnReconciliationService(hermes, conversations, transcript, turns,
                Clock.systemUTC(), new ReceptionTurnReconciliationService.Dependencies(
                        leases, policy, termsAcceptance, invocations));
    }

    @Bean
    public ReceptionOrchestrator receptionOrchestrator(
            HermesSessionService hermes, ReceptionConversationGateway conversations,
            CustomerFactGateway facts, ReceptionTranscriptGateway transcript,
            ReceptionTurnGateway turns, CommercialPolicyService policy,
            ReceptionTurnCoordinator coordinator, ActiveTurnLeaseService leases,
            DomainToolInvocationGateway invocations, ReceptionMetrics metrics,
            ReturningCustomerService returningCustomers, NonProspectPolicy nonProspectPolicy,
            TermsAcceptanceUseCase termsAcceptance,
            @Value("${hermes.poc.delay-threshold:5s}") String delayThreshold) {
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(hermes, conversations, facts, transcript, turns,
                policy, coordinator, leases, invocations, Clock.systemUTC(), metrics, returningCustomers,
                nonProspectPolicy, DurationStyle.detectAndParse(delayThreshold));
        orchestrator.setTermsAcceptanceUseCase(termsAcceptance);
        return orchestrator;
    }

    @Bean
    public HermesWebhookMessageHandler hermesWebhookMessageHandler(
            PocReceptionWorker worker,
            DeliveryDestinationRegistryGateway destinationRegistry) {
        return new HermesWebhookMessageHandler(worker, destinationRegistry);
    }

    @Bean(initMethod = "recover", destroyMethod = "close")
    public PocReceptionWorker pocReceptionWorker(ReceptionOrchestrator orchestrator,
                                                 PocPendingEventGateway pendingEvents,
            ReceptionTurnReconciliationService reconciliation,
                                                 HermesOutboundPublisher outboundPublisher,
                                                 HermesFailureHandoffService failureHandoff,
                                                @Value("${hermes.poc.worker-parallelism:4}") int parallelism,
                                                 @Value("${hermes.poc.claim-ttl:240s}") String claimTtl) {
        return new PocReceptionWorker(orchestrator, pendingEvents, reconciliation, parallelism,
                Clock.systemUTC(), DurationStyle.detectAndParse(claimTtl), outboundPublisher, failureHandoff);
    }

    @Bean
    public PocReceptionIngress pocReceptionIngress(ReceptionOrchestrator orchestrator,
                                                   MessageBatcher batcher,
                                                   MediaNormalizationService mediaNormalizationService,
                                                   PocPendingEventGateway pendingEvents,
                                                   PocReceptionWorker worker) {
        return new PocReceptionIngress(orchestrator, batcher, mediaNormalizationService,
                pendingEvents, worker, Clock.systemUTC());
    }

    @Bean
    public PocReceptionBatchFlushScheduler pocReceptionBatchFlushScheduler(PocReceptionIngress ingress,
                                                                            PocReceptionWorker worker) {
        return new PocReceptionBatchFlushScheduler(ingress, worker, Clock.systemUTC());
    }

    private static void configuredTerms(Map<String, TermsContentGateway.TermsContent> target,
                                        String serviceType, String version, String resource, String content) {
        if (version == null || version.isBlank() || resource == null || resource.isBlank()
                || content == null || content.isBlank()) {
            return;
        }
        target.put(serviceType, new TermsContentGateway.TermsContent(version, resource, content));
    }

    private static void requireDeliverySetting(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when delivery.outbox.enabled=true");
        }
    }
}
