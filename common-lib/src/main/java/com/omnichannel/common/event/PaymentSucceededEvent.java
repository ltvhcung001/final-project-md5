package com.omnichannel.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        String provider,
        String providerTxnId,
        BigDecimal amount,
        Instant occurredAt) {
}
