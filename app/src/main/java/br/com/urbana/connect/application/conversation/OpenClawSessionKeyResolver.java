package br.com.urbana.connect.application.conversation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OpenClawSessionKeyResolver {

    private static final String SESSION_KEY_PREFIX = "agent:urba:whatsapp:wa_";

    public String resolve(String phoneNumber) {
        return SESSION_KEY_PREFIX + shortHash(normalize(phoneNumber));
    }

    private String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.endsWith("@g.us")
            ? phoneNumber.replaceAll("[^0-9A-Za-z@._-]", "")
            : phoneNumber.replaceAll("[^0-9]", "");
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
