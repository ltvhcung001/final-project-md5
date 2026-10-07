package com.omnichannel.payment.messaging;

import com.omnichannel.common.event.EventNames;
import com.omnichannel.common.event.OrderCreatedEvent;
import com.omnichannel.payment.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private final PaymentService payments;

    public OrderCreatedListener(PaymentService payments) {
        this.payments = payments;
    }

    @RabbitListener(queues = EventNames.QUEUE_PAYMENT_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        payments.createForOrder(event);
    }
}
