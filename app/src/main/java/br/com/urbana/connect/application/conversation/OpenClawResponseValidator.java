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
        if (containsToolOutput(sanitized)) {
            return OpenClawResponseValidationResult.rejected("tool_output");
        }

        return OpenClawResponseValidationResult.accepted(sanitized);
    }

    private boolean containsToolOutput(String replyText) {
        String normalized = replyText.toLowerCase();
        return normalized.contains("<tool_code>")
            || normalized.contains("</tool_code>")
            || normalized.contains("default_api.")
            || normalized.contains("agents.md")
            || normalized.contains("soul.md")
            || normalized.contains("tools.md")
            || normalized.contains("function_call")
            || normalized.contains("\"tool_calls\"");
    }
}
