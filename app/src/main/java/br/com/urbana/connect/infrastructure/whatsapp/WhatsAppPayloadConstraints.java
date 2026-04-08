package br.com.urbana.connect.infrastructure.whatsapp;

final class WhatsAppPayloadConstraints {

    static final int REPLY_BUTTON_TITLE_LIMIT = 20;
    static final int LIST_BUTTON_TEXT_LIMIT = 20;
    static final int LIST_ROW_TITLE_LIMIT = 24;
    static final int LIST_ROW_DESCRIPTION_LIMIT = 72;
    static final int INTERACTIVE_BODY_TEXT_LIMIT = 1024;
    static final int TEXT_BODY_LIMIT = 4096;

    private WhatsAppPayloadConstraints() {
    }

    static String replyButtonTitle(String value) {
        return truncate(value, REPLY_BUTTON_TITLE_LIMIT);
    }

    static String listButtonText(String value) {
        return truncate(value, LIST_BUTTON_TEXT_LIMIT);
    }

    static String listRowTitle(String value) {
        return truncate(value, LIST_ROW_TITLE_LIMIT);
    }

    static String listRowDescription(String value) {
        return truncate(value, LIST_ROW_DESCRIPTION_LIMIT);
    }

    static String interactiveBodyText(String value) {
        return truncate(value, INTERACTIVE_BODY_TEXT_LIMIT);
    }

    static String textBody(String value) {
        return truncate(value, TEXT_BODY_LIMIT);
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        if (limit <= 3) {
            return value.substring(0, limit);
        }
        return value.substring(0, limit - 3) + "...";
    }
}
