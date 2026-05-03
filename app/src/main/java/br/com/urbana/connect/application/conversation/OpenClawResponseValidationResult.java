package br.com.urbana.connect.application.conversation;

public record OpenClawResponseValidationResult(
        boolean valid,
        String sanitizedText,
        String reason) {

    public static OpenClawResponseValidationResult accepted(String sanitizedText) {
        return new OpenClawResponseValidationResult(true, sanitizedText, null);
    }

    public static OpenClawResponseValidationResult rejected(String reason) {
        return new OpenClawResponseValidationResult(false, null, reason);
    }
}
