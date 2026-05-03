package br.com.urbana.connect.domain.conversation.model;

public record OpenClawTurnResult(
        OpenClawTurnStatus status,
        String text,
        String errorReason) {

    public static OpenClawTurnResult success(String text) {
        return new OpenClawTurnResult(OpenClawTurnStatus.SUCCESS, text, null);
    }

    public static OpenClawTurnResult timeout(String errorReason) {
        return new OpenClawTurnResult(OpenClawTurnStatus.TIMEOUT, null, errorReason);
    }

    public static OpenClawTurnResult error(String errorReason) {
        return new OpenClawTurnResult(OpenClawTurnStatus.ERROR, null, errorReason);
    }
}
