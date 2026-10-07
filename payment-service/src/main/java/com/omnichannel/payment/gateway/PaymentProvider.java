package com.omnichannel.payment.gateway;

import com.omnichannel.payment.entity.PaymentTransaction;

public interface PaymentProvider {

    /** VNPAY, MOMO or MOCK. */
    String name();

    /** URL the customer is redirected to in order to pay. */
    String createPaymentUrl(PaymentTransaction tx);
}
