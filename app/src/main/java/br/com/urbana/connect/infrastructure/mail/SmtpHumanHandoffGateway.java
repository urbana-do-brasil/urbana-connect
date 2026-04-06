package br.com.urbana.connect.infrastructure.mail;

import br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest;
import br.com.urbana.connect.domain.conversation.port.out.HumanHandoffGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpHumanHandoffGateway implements HumanHandoffGateway {

    private final JavaMailSender javaMailSender;
    private final String recipient;
    private final String sender;

    public SmtpHumanHandoffGateway(
            JavaMailSender javaMailSender,
            @Value("${handoff.human.recipient:comunicacao@urbanadobrasil.com}") String recipient,
            @Value("${spring.mail.username:}") String sender) {
        this.javaMailSender = javaMailSender;
        this.recipient = recipient;
        this.sender = sender;
    }

    @Override
    public void notifyTeam(HumanHandoffRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (sender != null && !sender.isBlank()) {
            message.setFrom(sender);
        }
        message.setTo(recipient);
        message.setSubject("Urba Connect - atendimento humano solicitado");
        message.setText("""
            Um cliente solicitou atendimento humano na Urba.

            Numero do cliente: %s
            Etapa atual: %s
            Servico selecionado: %s
            Forma de pagamento: %s
            Horario do evento: %s
            Ultima mensagem recebida: %s
            """.formatted(
            request.phoneNumber(),
            request.currentStep(),
            request.selectedService() == null ? "nao informado" : request.selectedService().name(),
            request.paymentMethod() == null ? "nao informada" : request.paymentMethod(),
            request.receivedAt(),
            request.latestMessage() == null || request.latestMessage().isBlank() ? "sem texto" : request.latestMessage()
        ));
        javaMailSender.send(message);
    }
}
