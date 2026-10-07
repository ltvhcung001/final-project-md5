package com.omnichannel.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/** Outcome of a provider callback after the signature has been checked. */
public record CallbackResult(boolean validSignature, UUID orderId, boolean success, String providerTxnId,
                             BigDecimal amount, String message) {

    public static CallbackResult invalid() {
        return new CallbackResult(false, null, false, null, null, "Invalid signature");
    }
}
