package com.omnichannel.common.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        String reason,
        Instant occurredAt) {
}
