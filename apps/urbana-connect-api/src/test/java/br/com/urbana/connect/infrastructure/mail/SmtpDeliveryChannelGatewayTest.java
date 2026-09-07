package br.com.urbana.connect.infrastructure.mail;

import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpDeliveryChannelGatewayTest {
    @Test
    void sendsOnlyHumanHandoffIntentsAndReturnsTheMessageId() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = mock(MimeMessage.class);
        when(sender.createMimeMessage()).thenReturn(message);
        when(message.getMessageID()).thenReturn("<smtp-1@example.test>");
        SmtpDeliveryChannelGateway gateway = new SmtpDeliveryChannelGateway(sender,
                "robot@example.test", "Handoff");

        var result = gateway.send(intent(), "hml-support@example.test");

        assertThat(result.providerMessageId()).isEqualTo("<smtp-1@example.test>");
        verify(sender).send(message);
    }

    @Test
    void keepsAProviderTransportFailureAmbiguous() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = mock(MimeMessage.class);
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("smtp timeout")).when(sender).send(message);
        SmtpDeliveryChannelGateway gateway = new SmtpDeliveryChannelGateway(sender, "", "Handoff");

        assertThatThrownBy(() -> gateway.send(intent(), "hml-support@example.test"))
                .isInstanceOf(DeliveryDispatchException.class)
                .extracting("phase")
                .isEqualTo(DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN);
    }

    @Test
    void rejectsNonEmailIntentsBeforePreparingAMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpDeliveryChannelGateway gateway = new SmtpDeliveryChannelGateway(sender, "", "Handoff");

        assertThatThrownBy(() -> gateway.send(
                DeliveryOutbox.pending("event", DeliveryOutboxKind.WHATSAPP_MENU, "opaque", "payload",
                        "corr", Instant.parse("2026-09-03T12:00:00Z")), "dest"))
                .isInstanceOf(DeliveryDispatchException.class)
                .hasMessageContaining("non-email");
    }

    private static DeliveryOutbox intent() {
        return DeliveryOutbox.pending("event", DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF,
                "support-mailbox", "reason=HERMES_FAILED;contactRef=wa:hash",
                "correlation", Instant.parse("2026-09-03T12:00:00Z"));
    }
}
