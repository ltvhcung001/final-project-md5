package com.omnichannel.order.messaging;

import com.omnichannel.common.event.EventNames;
import com.omnichannel.common.event.PaymentFailedEvent;
import com.omnichannel.common.event.PaymentSucceededEvent;
import com.omnichannel.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Saga step 2: react to the payment outcome. Handlers are idempotent, so redelivery is harmless. */
@Component
public class PaymentResultListener {

    private final OrderService orders;

    public PaymentResultListener(OrderService orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = EventNames.QUEUE_ORDER_PAYMENT_SUCCEEDED)
    public void onSucceeded(PaymentSucceededEvent event) {
        orders.onPaymentSucceeded(event.orderId());
    }

    @RabbitListener(queues = EventNames.QUEUE_ORDER_PAYMENT_FAILED)
    public void onFailed(PaymentFailedEvent event) {
        orders.cancel(event.orderId(), "Payment failed: " + event.reason());
    }
}
