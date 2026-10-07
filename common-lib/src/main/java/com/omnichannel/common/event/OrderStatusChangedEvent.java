package com.omnichannel.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID eventId,
        UUID orderId,
        String userId,
        String userEmail,
        OrderStatus oldStatus,
        OrderStatus newStatus,
        Instant occurredAt) {
}
