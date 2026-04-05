package br.com.urbana.connect.domain.conversation.port.out;

public interface WhatsAppMessageGateway {

    void sendGreeting(String phoneNumber);
}
