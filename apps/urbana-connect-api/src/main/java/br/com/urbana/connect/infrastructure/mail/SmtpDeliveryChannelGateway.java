package br.com.urbana.connect.infrastructure.mail;

import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Objects;

/**
 * SMTP adapter for durable human-handoff intents. It only accepts the
 * structured outbox kind and requires a Message-ID before reporting SENT;
 * this prevents a successful local method call from masquerading as provider
 * acceptance.
 */
public final class SmtpDeliveryChannelGateway implements DeliveryChannelGateway {
    private final JavaMailSender sender;
    private final String from;
    private final String subject;

    public SmtpDeliveryChannelGateway(JavaMailSender sender, String from, String subject) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.from = from == null ? "" : from.trim();
        this.subject = subject == null || subject.isBlank()
                ? "Urba Connect - atendimento humano solicitado" : subject;
    }

    @Override
    public DeliveryResult send(DeliveryOutbox outbox, String resolvedDestination) {
        if (outbox == null || outbox.kind() != DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF) {
            throw new DeliveryDispatchException("SMTP adapter received a non-email intent",
                    DeliveryDispatchPhase.PRE_DISPATCH);
        }
        if (resolvedDestination == null || resolvedDestination.isBlank()) {
            throw new DeliveryDispatchException("SMTP destination is blank", DeliveryDispatchPhase.PRE_DISPATCH);
        }

        MimeMessage message;
        try {
            message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo(resolvedDestination);
            if (!from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setSubject(subject);
            helper.setText(outbox.payload(), false);
        } catch (MessagingException | RuntimeException failure) {
            throw new DeliveryDispatchException("SMTP message could not be prepared",
                    DeliveryDispatchPhase.PRE_DISPATCH, failure);
        }

        try {
            sender.send(message);
        } catch (RuntimeException failure) {
            // SMTP may have accepted the DATA command before the client saw a
            // transport error; reconciliation is safer than a blind resend.
            throw new DeliveryDispatchException("SMTP dispatch outcome is unknown",
                    DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN, failure);
        }

        String providerMessageId;
        try {
            providerMessageId = message.getMessageID();
        } catch (MessagingException failure) {
            throw new DeliveryDispatchException("SMTP Message-ID could not be read",
                    DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN, failure);
        }
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new DeliveryDispatchException("SMTP provider did not expose a Message-ID",
                    DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN);
        }
        return new DeliveryResult(providerMessageId);
    }
}
