package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.reception.HermesWebhookMessageHandler;
import br.com.urbana.connect.application.reception.WebhookInbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookControllerConfigurationTest {

    @Test
    void rejectsConflictingHermesAndInboundOnlyProfiles() {
        var beanFactory = new StaticListableBeanFactory();

        assertThatThrownBy(() -> new WebhookController(
                "",
                null,
                beanFactory.getBeanProvider(HermesWebhookMessageHandler.class),
                beanFactory.getBeanProvider(WebhookInbox.class),
                true,
                true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hermes and inbound-only webhook profiles cannot be enabled together");
    }

    @Test
    void rejectsInboundOnlyProfileWithoutWebhookInboxBean() {
        var beanFactory = new StaticListableBeanFactory();

        assertThatThrownBy(() -> new WebhookController(
                "",
                null,
                beanFactory.getBeanProvider(HermesWebhookMessageHandler.class),
                beanFactory.getBeanProvider(WebhookInbox.class),
                false,
                true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WebhookInbox is required when inbound-only mode is active");
    }
}
