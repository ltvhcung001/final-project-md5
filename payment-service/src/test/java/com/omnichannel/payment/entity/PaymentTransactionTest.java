package com.omnichannel.payment.entity;

import com.omnichannel.payment.entity.PaymentTransaction.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTransactionTest {

    private PaymentTransaction tx() {
        return new PaymentTransaction(UUID.randomUUID(), "u", BigDecimal.TEN, "MOCK");
    }

    @Test
    void onlyTheFirstResultCounts() {
        var t = tx();
        assertTrue(t.complete(Status.SUCCEEDED, "T1", null));
        assertFalse(t.complete(Status.FAILED, "T2", "late failure")); // duplicate / conflicting webhook
        assertEquals(Status.SUCCEEDED, t.getStatus());
        assertEquals("T1", t.getProviderTxnId());
    }

    @Test
    void refundRequiresSuccessfulPayment() {
        var pending = tx();
        assertFalse(pending.refund());

        var paid = tx();
        paid.complete(Status.SUCCEEDED, "T1", null);
        assertTrue(paid.refund());
        assertFalse(paid.refund());
        assertEquals(Status.REFUNDED, paid.getStatus());
    }
}
