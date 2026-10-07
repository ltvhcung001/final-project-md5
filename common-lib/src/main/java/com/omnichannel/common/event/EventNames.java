package com.omnichannel.common.event;

/** RabbitMQ topology shared by producers and consumers. */
public final class EventNames {

    public static final String EXCHANGE = "omnichannel.events";

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_STATUS_CHANGED = "order.status-changed";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";

    public static final String QUEUE_PAYMENT_ORDER_CREATED = "payment.order-created";
    public static final String QUEUE_ORDER_PAYMENT_RESULT = "order.payment-result";
    public static final String QUEUE_NOTIFICATION = "notification.events";

    private EventNames() {
    }
}
