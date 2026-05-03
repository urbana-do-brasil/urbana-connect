package br.com.urbana.connect.application.conversation;

import org.springframework.stereotype.Component;

@Component
public class OpenClawSessionKeyResolver {

    public String resolve(String phoneNumber) {
        String normalizedPhone = phoneNumber == null
            ? ""
            : phoneNumber.endsWith("@g.us")
                ? phoneNumber.replaceAll("[^0-9A-Za-z@._-]", "")
                : phoneNumber.replaceAll("[^0-9]", "");
        return "whatsapp:" + normalizedPhone;
    }
}
