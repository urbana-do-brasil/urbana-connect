package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.reception.tools.DomainToolInvocationUseCase;
import br.com.urbana.connect.application.reception.tools.DomainToolService;
import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoActiveTurnLeaseGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoAgentSessionLinkGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoDomainToolInvocationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoCustomerFactGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoReceptionConversationGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoReceptionTranscriptGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.MongoReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataActiveTurnLeaseRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataAgentSessionLinkRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataDomainToolInvocationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataCustomerFactRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionConversationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionMessageRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionTurnRepository;
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

/** Explicit POC wiring; production webhook wiring is intentionally untouched. */
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
                                                       ReceptionTranscriptGateway transcript) {
        return new StatefulDomainToolService(policy, conversations, facts, transcript);
    }

    @Bean
    public ActiveTurnLeaseService activeTurnLeaseService(ActiveTurnLeaseGateway gateway,
                                                         @Value("${hermes.sessions.lease-ttl:60s}") String ttl) {
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
            @Value("${hermes.sessions.timeout:30s}") String timeout) {
        return new HttpHermesSessionsGateway(builder, baseUrl, apiKey, model, reasoningEffort,
                DurationStyle.detectAndParse(timeout));
    }

    @Bean
    public HermesSessionService hermesSessionService(HermesSessionsGateway sessions,
                                                     AgentSessionLinkGateway links) {
        return new HermesSessionService(sessions, links);
    }

    @Bean
    public ReceptionOrchestrator receptionOrchestrator(
            HermesSessionService hermes, ReceptionConversationGateway conversations,
            CustomerFactGateway facts, ReceptionTranscriptGateway transcript,
            ReceptionTurnGateway turns, CommercialPolicyService policy,
            ReceptionTurnCoordinator coordinator, ActiveTurnLeaseService leases,
            DomainToolInvocationGateway invocations, ReceptionMetrics metrics,
            ReturningCustomerService returningCustomers, NonProspectPolicy nonProspectPolicy) {
        return new ReceptionOrchestrator(hermes, conversations, facts, transcript, turns,
                policy, coordinator, leases, invocations, Clock.systemUTC(), metrics, returningCustomers,
                nonProspectPolicy);
    }

    @Bean
    public PocReceptionIngress pocReceptionIngress(ReceptionOrchestrator orchestrator,
                                                   MessageBatcher batcher,
                                                   MediaNormalizationService mediaNormalizationService) {
        return new PocReceptionIngress(orchestrator, batcher, mediaNormalizationService);
    }

    @Bean
    public PocReceptionBatchFlushScheduler pocReceptionBatchFlushScheduler(PocReceptionIngress ingress) {
        return new PocReceptionBatchFlushScheduler(ingress, Clock.systemUTC());
    }
}
