package br.com.urbana.connect.application.conversation;

public record ResponseValidationResult(
        boolean valid,
        String reason) {

    public static ResponseValidationResult accepted() {
        return new ResponseValidationResult(true, null);
    }

    public static ResponseValidationResult rejected(String reason) {
        return new ResponseValidationResult(false, reason);
    }
}
