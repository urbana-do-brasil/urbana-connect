package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.reception.tools.DomainToolInvocationUseCase;
import br.com.urbana.connect.application.reception.tools.DomainToolService;
import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
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
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataPocPendingEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/** Hermes reception wiring shared by the local simulator and the WhatsApp POC route. */
@Configuration
@EnableScheduling
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
                                                       TermsAcceptanceUseCase termsAcceptance) {
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);
        tools.setTermsAcceptanceUseCase(termsAcceptance);
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
    public CommercialPolicyService commercialPolicyService() {
        return new CommercialPolicyService();
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
                Clock.systemUTC(), leases, policy, termsAcceptance, invocations);
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
            ReceptionOrchestrator orchestrator, WhatsAppMessageGateway whatsapp) {
        return new HermesWebhookMessageHandler(orchestrator, whatsapp);
    }

    @Bean(initMethod = "recover", destroyMethod = "close")
    public PocReceptionWorker pocReceptionWorker(ReceptionOrchestrator orchestrator,
                                                 PocPendingEventGateway pendingEvents,
            ReceptionTurnReconciliationService reconciliation,
                                                @Value("${hermes.poc.worker-parallelism:4}") int parallelism,
                                                 @Value("${hermes.poc.claim-ttl:240s}") String claimTtl) {
        return new PocReceptionWorker(orchestrator, pendingEvents, reconciliation, parallelism,
                Clock.systemUTC(), DurationStyle.detectAndParse(claimTtl));
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
}
