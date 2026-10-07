package com.omnichannel.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String userId,
        String userEmail,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemEvent> items,
        Instant occurredAt) {
}
