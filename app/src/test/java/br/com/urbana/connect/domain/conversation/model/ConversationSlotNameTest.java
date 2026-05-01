package br.com.urbana.connect.domain.conversation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationSlotNameTest {

    @Test
    void shouldResolveSlotByExternalKey() {
        assertThat(ConversationSlotName.fromKey("needsDiscoveryHelp"))
            .isEqualTo(ConversationSlotName.NEEDS_DISCOVERY_HELP);
        assertThat(ConversationSlotName.fromKey("confirmedService"))
            .isEqualTo(ConversationSlotName.CONFIRMED_SERVICE);
    }

    @Test
    void shouldExposeExternalKey() {
        assertThat(ConversationSlotName.SUGGESTED_SERVICE.key()).isEqualTo("suggestedService");
    }

    @Test
    void shouldRejectUnknownSlotKey() {
        assertThatThrownBy(() -> ConversationSlotName.fromKey("slotInvalido"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown conversation slot");
    }
}
