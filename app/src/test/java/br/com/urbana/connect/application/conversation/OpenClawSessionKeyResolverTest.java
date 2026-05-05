package br.com.urbana.connect.application.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawSessionKeyResolverTest {

    private final OpenClawSessionKeyResolver resolver = new OpenClawSessionKeyResolver();

    @Test
    void shouldReturnStableSessionKeyForSamePhoneNumber() {
        String sessionKey = resolver.resolve("+55 (83) 99999-1111");

        assertThat(sessionKey).isEqualTo(resolver.resolve("+55 (83) 99999-1111"));
        assertThat(sessionKey)
            .startsWith("agent:urba:whatsapp:wa_")
            .hasSize("agent:urba:whatsapp:wa_".length() + 16);
    }

    @Test
    void shouldReturnDifferentSessionKeysForDifferentPhoneNumbers() {
        assertThat(resolver.resolve("+5583999991111"))
            .isNotEqualTo(resolver.resolve("+5583999992222"));
    }

    @Test
    void shouldNotExposeRawPhoneNumberInSessionKey() {
        assertThat(resolver.resolve("+55 (83) 99999-1111"))
            .doesNotContain("5583999991111");
    }
}
