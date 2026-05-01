package br.com.urbana.connect.domain.conversation.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ConversationContext(
        String paymentMethod,
        Map<ConversationSlotName, ConversationSlotValue> slots) {

    public ConversationContext(String paymentMethod) {
        this(paymentMethod, Map.of());
    }

    public ConversationContext {
        slots = slots == null || slots.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new EnumMap<>(slots));
    }

    public static ConversationContext empty() {
        return new ConversationContext(null, Map.of());
    }

    public ConversationContext withPaymentMethod(String paymentMethod) {
        return new ConversationContext(paymentMethod, slots);
    }

    public ConversationContext withSlot(ConversationSlotName slotName, ConversationSlotValue slotValue) {
        EnumMap<ConversationSlotName, ConversationSlotValue> updatedSlots = new EnumMap<>(ConversationSlotName.class);
        updatedSlots.putAll(slots);
        updatedSlots.put(slotName, slotValue == null ? null : slotValue.normalized());
        updatedSlots.values().removeIf(Objects::isNull);
        return new ConversationContext(paymentMethod, updatedSlots);
    }

    public ConversationContext withoutSlot(ConversationSlotName slotName) {
        if (!slots.containsKey(slotName)) {
            return this;
        }

        EnumMap<ConversationSlotName, ConversationSlotValue> updatedSlots = new EnumMap<>(ConversationSlotName.class);
        updatedSlots.putAll(slots);
        updatedSlots.remove(slotName);
        return new ConversationContext(paymentMethod, updatedSlots);
    }

    public Optional<ConversationSlotValue> slot(ConversationSlotName slotName) {
        return Optional.ofNullable(slots.get(slotName));
    }

    public Optional<String> slotValue(ConversationSlotName slotName) {
        return slot(slotName).map(ConversationSlotValue::value);
    }

    public boolean hasSlotAtLeast(ConversationSlotName slotName, ConversationSlotLevel requiredLevel) {
        return slot(slotName)
            .map(slotValue -> slotValue.satisfies(requiredLevel))
            .orElse(false);
    }
}
