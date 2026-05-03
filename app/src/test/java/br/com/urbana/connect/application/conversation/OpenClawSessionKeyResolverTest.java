package br.com.urbana.connect.application.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawSessionKeyResolverTest {

    private final OpenClawSessionKeyResolver resolver = new OpenClawSessionKeyResolver();

    @Test
    void shouldReturnStableSessionKeyForSamePhoneNumber() {
        assertThat(resolver.resolve("+55 (83) 99999-1111"))
            .isEqualTo("whatsapp:5583999991111");
        assertThat(resolver.resolve("+55 (83) 99999-1111"))
            .isEqualTo("whatsapp:5583999991111");
    }

    @Test
    void shouldReturnDifferentSessionKeysForDifferentPhoneNumbers() {
        assertThat(resolver.resolve("+5583999991111"))
            .isNotEqualTo(resolver.resolve("+5583999992222"));
    }
}
