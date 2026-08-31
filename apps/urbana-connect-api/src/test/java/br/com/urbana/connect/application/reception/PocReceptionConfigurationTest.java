package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.PocPendingEventGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.application.reception.tools.DomainToolInvocationUseCase;
import br.com.urbana.connect.application.reception.tools.DomainToolService;
import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataCustomerFactRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionConversationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionMessageRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataReceptionTurnRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataActiveTurnLeaseRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataAgentSessionLinkRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataDomainToolInvocationRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataPocPendingEventRepository;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.SpringDataTermsConsentAuditRepository;
import br.com.urbana.connect.interfaces.rest.poc.DomainToolController;
import br.com.urbana.connect.interfaces.rest.poc.ConversationSimulatorController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PocReceptionConfigurationTest {
    @Test
    void loadsAllPocFoundationBeansAndInjectsMongoTemplateIntoProductionGateways() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        SpringDataActiveTurnLeaseRepository leaseRepository = mock(SpringDataActiveTurnLeaseRepository.class);
        SpringDataAgentSessionLinkRepository linkRepository = mock(SpringDataAgentSessionLinkRepository.class);
        SpringDataDomainToolInvocationRepository invocationRepository =
                mock(SpringDataDomainToolInvocationRepository.class);
        SpringDataCustomerFactRepository factRepository = mock(SpringDataCustomerFactRepository.class);
        SpringDataReceptionConversationRepository conversationRepository =
                mock(SpringDataReceptionConversationRepository.class);
        SpringDataReceptionMessageRepository messageRepository = mock(SpringDataReceptionMessageRepository.class);
        SpringDataReceptionTurnRepository turnRepository = mock(SpringDataReceptionTurnRepository.class);
        SpringDataPocPendingEventRepository pendingEventRepository = mock(SpringDataPocPendingEventRepository.class);
        SpringDataTermsConsentAuditRepository termsConsentAuditRepository = mock(SpringDataTermsConsentAuditRepository.class);
        new ApplicationContextRunner()
                .withUserConfiguration(PocReceptionConfiguration.class, ControllerConfiguration.class)
                .withBean(SpringDataActiveTurnLeaseRepository.class, () -> leaseRepository)
                .withBean(SpringDataAgentSessionLinkRepository.class, () -> linkRepository)
                .withBean(SpringDataDomainToolInvocationRepository.class, () -> invocationRepository)
                .withBean(SpringDataCustomerFactRepository.class, () -> factRepository)
                .withBean(SpringDataReceptionConversationRepository.class, () -> conversationRepository)
                .withBean(SpringDataReceptionMessageRepository.class, () -> messageRepository)
                .withBean(SpringDataReceptionTurnRepository.class, () -> turnRepository)
                .withBean(SpringDataPocPendingEventRepository.class, () -> pendingEventRepository)
                .withBean(SpringDataTermsConsentAuditRepository.class, () -> termsConsentAuditRepository)
                .withBean(MongoTemplate.class, () -> mongoTemplate)
                .withBean(WhatsAppMessageGateway.class, () -> mock(WhatsAppMessageGateway.class))
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withPropertyValues(
                        "hermes.poc.enabled=true",
                        "hermes.poc.delay-threshold=75ms",
                        "hermes.sessions.internal-tool-token=test-token",
                        "hermes.sessions.internal-tool-principal=hermes-urbana-domain")
                .run(context -> {
                    assertThat(context).hasSingleBean(DomainToolController.class);
                    assertThat(context).hasSingleBean(ConversationSimulatorController.class);
                    assertThat(context).hasSingleBean(HermesSessionService.class);
                    assertThat(context).hasSingleBean(ReceptionOrchestrator.class);
                    assertThat(context).hasSingleBean(HermesWebhookMessageHandler.class);
                    assertThat(context).hasSingleBean(NonProspectPolicy.class);
                    assertThat(context).hasSingleBean(MessageBatcher.class);
                    assertThat(context).hasSingleBean(MediaNormalizationService.class);
                    assertThat(context).hasSingleBean(PocReceptionIngress.class);
                    assertThat(context).hasSingleBean(PocPendingEventGateway.class);
                    assertThat(context).hasSingleBean(ReceptionTurnReconciliationService.class);
                    assertThat(context).hasSingleBean(PocReceptionWorker.class);
                    assertThat(context).hasSingleBean(DomainToolService.class);
                    assertThat(context).hasSingleBean(TermsAcceptanceUseCase.class);
                    assertThat(context).hasSingleBean(
                            br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway.class);
                    assertThat(context.getBean(DomainToolService.class)).isInstanceOf(StatefulDomainToolService.class);
                    assertThat(context).hasSingleBean(ActiveTurnLeaseService.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(ReceptionOrchestrator.class), "delayThreshold"))
                            .isEqualTo(Duration.ofMillis(75));
                    assertThat(ReflectionTestUtils.getField(context.getBean(ActiveTurnLeaseService.class), "ttl"))
                            .isEqualTo(Duration.ofSeconds(240));
                    assertThat(ReflectionTestUtils.getField(context.getBean(PocReceptionWorker.class), "claimTtl"))
                            .isEqualTo(Duration.ofSeconds(240));
                    assertThat(context).hasSingleBean(ActiveTurnLeaseGateway.class);
                    assertThat(context).hasSingleBean(AgentSessionLinkGateway.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(ActiveTurnLeaseGateway.class), "template"))
                            .isSameAs(mongoTemplate);
                    assertThat(ReflectionTestUtils.getField(context.getBean(AgentSessionLinkGateway.class), "template"))
                            .isSameAs(mongoTemplate);
                    assertThat(ReflectionTestUtils.getField(context.getBean(PocPendingEventGateway.class), "template"))
                            .isSameAs(mongoTemplate);
                    PocReceptionIngress ingress = context.getBean(PocReceptionIngress.class);
                    assertThat(ReflectionTestUtils.getField(ingress, "pendingEvents"))
                            .isSameAs(context.getBean(PocPendingEventGateway.class));
                    assertThat(ReflectionTestUtils.getField(ingress, "worker"))
                            .isSameAs(context.getBean(PocReceptionWorker.class));
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllerConfiguration {
        @Bean
        DomainToolController domainToolController(
                DomainToolInvocationUseCase invocation,
                @Value("${hermes.sessions.internal-tool-token:}") String token,
                @Value("${hermes.sessions.internal-tool-principal:hermes-urbana-domain}") String principal) {
            return new DomainToolController(invocation, token, principal);
        }

        @Bean
        ConversationSimulatorController conversationSimulatorController(ReceptionOrchestrator orchestrator,
                                                                         PocReceptionIngress ingress) {
            return new ConversationSimulatorController(orchestrator, ingress);
        }
    }
}
