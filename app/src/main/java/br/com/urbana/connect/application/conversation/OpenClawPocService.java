package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnResult;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.OpenClawClient;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OpenClawPocService {

    private static final Logger log = LoggerFactory.getLogger(OpenClawPocService.class);

    private final ConversationLifecycleService conversationLifecycleService;
    private final ConversationMessageGateway conversationMessageGateway;
    private final OpenClawClient openClawClient;
    private final OpenClawSessionKeyResolver sessionKeyResolver;
    private final OpenClawResponseValidator responseValidator;
    private final WhatsAppMessageGateway whatsAppMessageGateway;
    private final String fallbackText;
    private final int maxReplyLength;
    private final int historyLimit;

    public OpenClawPocService(
            ConversationLifecycleService conversationLifecycleService,
            ConversationMessageGateway conversationMessageGateway,
            OpenClawClient openClawClient,
            OpenClawSessionKeyResolver sessionKeyResolver,
            OpenClawResponseValidator responseValidator,
            WhatsAppMessageGateway whatsAppMessageGateway,
            @Value("${openclaw.poc.fallback-text:Estou com uma instabilidade momentânea aqui. Pode me enviar sua mensagem novamente em instantes?}") String fallbackText,
            @Value("${openclaw.poc.max-reply-length:1024}") int maxReplyLength,
            @Value("${openclaw.poc.history-limit:8}") int historyLimit) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.conversationMessageGateway = conversationMessageGateway;
        this.openClawClient = openClawClient;
        this.sessionKeyResolver = sessionKeyResolver;
        this.responseValidator = responseValidator;
        this.whatsAppMessageGateway = whatsAppMessageGateway;
        this.fallbackText = fallbackText;
        this.maxReplyLength = maxReplyLength;
        this.historyLimit = historyLimit;
    }

    public void handleTextTurn(InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        Conversation conversation = conversationLifecycleService.resumeOrStart(inboundMessage.phoneNumber(), receivedAt);
        if (!persistInboundMessage(conversation, inboundMessage, receivedAt)) {
            return;
        }

        String correlationId = UUID.randomUUID().toString();
        String sessionKey = sessionKeyResolver.resolve(inboundMessage.phoneNumber());
        String promptText = buildPromptText(conversation, inboundMessage.textBody());
        Instant startedAt = Instant.now();

        OpenClawTurnResult turnResult = openClawClient.sendTurn(new OpenClawTurnRequest(
            sessionKey,
            promptText,
            inboundMessage.phoneNumber(),
            conversation.id(),
            receivedAt.toString()
        ));

        long latencyMillis = Duration.between(startedAt, Instant.now()).toMillis();
        if (turnResult.status() != br.com.urbana.connect.domain.conversation.model.OpenClawTurnStatus.SUCCESS) {
            logTurn(correlationId, inboundMessage.phoneNumber(), sessionKey, "fallback", latencyMillis, turnResult.errorReason());
            whatsAppMessageGateway.sendTextMessage(inboundMessage.phoneNumber(), fallbackText);
            return;
        }

        OpenClawResponseValidationResult validationResult = responseValidator.validate(turnResult.text(), maxReplyLength);
        if (!validationResult.valid()) {
            logTurn(correlationId, inboundMessage.phoneNumber(), sessionKey, "fallback", latencyMillis, validationResult.reason());
            whatsAppMessageGateway.sendTextMessage(inboundMessage.phoneNumber(), fallbackText);
            return;
        }

        whatsAppMessageGateway.sendTextMessage(inboundMessage.phoneNumber(), validationResult.sanitizedText());
        logTurn(correlationId, inboundMessage.phoneNumber(), sessionKey, "success", latencyMillis, null);
    }

    private boolean persistInboundMessage(Conversation conversation, InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        if (conversation.id() == null) {
            return true;
        }

        try {
            conversationMessageGateway.save(ConversationMessage.inbound(
                conversation.id(),
                inboundMessage.phoneNumber(),
                ConversationMessageType.TEXT,
                inboundMessage.textBody(),
                null,
                blankToNull(inboundMessage.providerMessageId()),
                receivedAt,
                conversation.currentStep().name()
            ));
            return true;
        } catch (DuplicateKeyException exception) {
            if (log.isInfoEnabled()) {
                log.info(
                    "OpenClaw POC inbound duplicado ignorado: phoneNumber={} providerMessageId={}",
                    maskPhoneNumber(inboundMessage.phoneNumber()),
                    inboundMessage.providerMessageId()
                );
            }
            return false;
        }
    }

    private void logTurn(
            String correlationId,
            String phoneNumber,
            String sessionKey,
            String status,
            long latencyMillis,
            String reason) {
        if (reason == null || reason.isBlank()) {
            log.info(
                "OpenClaw POC turno: correlationId={} phoneNumber={} sessionKey={} status={} latencyMs={}",
                correlationId,
                maskPhoneNumber(phoneNumber),
                sessionKey,
                status,
                latencyMillis
            );
            return;
        }

        log.warn(
            "OpenClaw POC turno: correlationId={} phoneNumber={} sessionKey={} status={} latencyMs={} reason={}",
            correlationId,
            maskPhoneNumber(phoneNumber),
            sessionKey,
            status,
            latencyMillis,
            reason
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String buildPromptText(Conversation conversation, String currentText) {
        if (conversation.id() == null || historyLimit <= 1) {
            return currentText;
        }

        List<ConversationMessage> history = conversationMessageGateway.findRecentByConversationId(conversation.id(), historyLimit);
        if (history == null) {
            return currentText;
        }

        List<ConversationMessage> textHistory = history.stream()
            .filter(message -> message.rawText() != null && !message.rawText().isBlank())
            .toList();
        if (textHistory.size() <= 1) {
            return currentText;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Historico recente da conversa no WhatsApp. Use isto apenas como contexto e responda a ultima mensagem do cliente.\n\n");
        for (ConversationMessage message : textHistory) {
            prompt
                .append(message.direction() == br.com.urbana.connect.domain.conversation.model.ConversationMessageDirection.INBOUND ? "Cliente" : "Urba")
                .append(": ")
                .append(truncateForPrompt(message.rawText()))
                .append("\n");
        }
        prompt.append("\nResponda somente com a proxima mensagem da Urba para o cliente.");
        return prompt.toString();
    }

    private String truncateForPrompt(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "...";
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank() || phoneNumber.length() <= 7) {
            return "***";
        }
        int prefixLength = Math.min(5, phoneNumber.length() - 4);
        return phoneNumber.substring(0, prefixLength) + "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
