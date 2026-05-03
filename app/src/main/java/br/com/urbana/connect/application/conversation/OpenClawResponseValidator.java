package br.com.urbana.connect.application.conversation;

import org.springframework.stereotype.Component;

@Component
public class OpenClawResponseValidator {

    public OpenClawResponseValidationResult validate(String replyText, int maxReplyLength) {
        if (replyText == null || replyText.isBlank()) {
            return OpenClawResponseValidationResult.rejected("empty_reply");
        }

        String sanitized = replyText.trim();
        if (sanitized.length() > maxReplyLength) {
            return OpenClawResponseValidationResult.rejected("reply_too_long");
        }

        return OpenClawResponseValidationResult.accepted(sanitized);
    }
}
