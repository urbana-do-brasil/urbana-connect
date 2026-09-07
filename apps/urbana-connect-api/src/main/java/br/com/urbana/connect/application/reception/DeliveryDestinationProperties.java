package br.com.urbana.connect.application.reception;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Operational destination registry; values are supplied only by environment configuration. */
@ConfigurationProperties(prefix = "delivery.outbox.destinations")
public class DeliveryDestinationProperties {
    private String supportMailbox;
    private Map<String, String> contactAddresses = new LinkedHashMap<>();

    public String getSupportMailbox() { return supportMailbox; }
    public void setSupportMailbox(String supportMailbox) { this.supportMailbox = supportMailbox; }
    public Map<String, String> getContactAddresses() { return contactAddresses; }
    public void setContactAddresses(Map<String, String> contactAddresses) {
        this.contactAddresses = contactAddresses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(contactAddresses);
    }
}
