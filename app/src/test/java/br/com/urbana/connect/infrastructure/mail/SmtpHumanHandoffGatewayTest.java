package br.com.urbana.connect.infrastructure.mail;

import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpHumanHandoffGatewayTest {

    @Test
    void shouldSendHumanHandoffEmailWithConversationContext() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        SmtpHumanHandoffGateway gateway = new SmtpHumanHandoffGateway(
            javaMailSender,
            "comunicacao@urbanadobrasil.com",
            "robot@urbanadobrasil.com"
        );

        gateway.notifyTeam(new HumanHandoffRequest(
            "+5583999999999",
            ConversationStep.AWAITING_CONFIRMATION,
            ServiceType.DECOR,
            "PIX",
            List.of("USER: quero falar com alguém", "URBA_BOT: Iremos repassar sua dúvida"),
            Instant.parse("2026-04-06T10:30:00Z")
        ));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("comunicacao@urbanadobrasil.com");
        assertThat(message.getFrom()).isEqualTo("robot@urbanadobrasil.com");
        assertThat(message.getSubject()).contains("atendimento humano");
        assertThat(message.getText()).contains("+5583999999999");
        assertThat(message.getText()).contains("AWAITING_CONFIRMATION");
        assertThat(message.getText()).contains("DECOR");
        assertThat(message.getText()).contains("PIX");
        assertThat(message.getText()).contains("USER: quero falar com alguém");
        assertThat(message.getText()).contains("URBA_BOT: Iremos repassar sua dúvida");
    }
}
