package com.omnichannel.payment.dto;

import com.omnichannel.payment.entity.PaymentTransaction;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentResponse(UUID orderId, String status, String provider, BigDecimal amount,
                                  String paymentUrl, String failureReason) {
        public static PaymentResponse of(PaymentTransaction t) {
            return new PaymentResponse(t.getOrderId(), t.getStatus().name(), t.getProvider(), t.getAmount(),
                    t.getPaymentUrl(), t.getFailureReason());
        }
    }

    public record ReconciliationRow(String status, long count, BigDecimal total) {
    }
}
