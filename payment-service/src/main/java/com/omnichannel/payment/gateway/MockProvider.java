package com.omnichannel.payment.gateway;

import com.omnichannel.payment.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fake provider for demos and load tests: the "payment page" is our own endpoint
 * POST /api/payments/mock/{orderId}/complete?success=true|false (enabled with payment.mock.enabled).
 */
@Component
public class MockProvider implements PaymentProvider {

    private final boolean enabled;

    public MockProvider(@Value("${payment.mock.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public String createPaymentUrl(PaymentTransaction tx) {
        if (!enabled) {
            throw new IllegalStateException("Mock payments are disabled");
        }
        return "/api/payments/mock/" + tx.getOrderId() + "/complete";
    }
}
