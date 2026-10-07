package com.omnichannel.notification.messaging;

import com.omnichannel.common.event.EventNames;
import com.omnichannel.common.event.OrderStatus;
import com.omnichannel.common.event.OrderStatusChangedEvent;
import com.omnichannel.notification.document.DeliveryLog;
import com.omnichannel.notification.repository.DeliveryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final JavaMailSender mailSender;
    private final DeliveryLogRepository logs;
    private final String from;

    public NotificationListener(JavaMailSender mailSender, DeliveryLogRepository logs,
                                @Value("${notification.from}") String from) {
        this.mailSender = mailSender;
        this.logs = logs;
        this.from = from;
    }

    @RabbitListener(queues = EventNames.QUEUE_NOTIFICATION)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        String eventId = event.eventId().toString();
        if (logs.existsByEventIdAndStatus(eventId, DeliveryLog.Status.SENT)) {
            return; // redelivered message, already handled
        }
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            log.warn("Order {} has no customer email, skipping notification", event.orderId());
            return;
        }
        String subject = subjectFor(event);
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(event.userEmail());
            mail.setSubject(subject);
            mail.setText(bodyFor(event));
            mailSender.send(mail);
            // SMS / push are mocked: a real deployment would call a provider here
            log.info("[SMS-MOCK] to customer of order {}: {}", event.orderId(), subject);
            logs.save(new DeliveryLog(eventId, event.userEmail(), subject, DeliveryLog.Status.SENT, null));
        } catch (RuntimeException ex) {
            logs.save(new DeliveryLog(eventId, event.userEmail(), subject, DeliveryLog.Status.FAILED, ex.getMessage()));
            throw ex; // listener retry, then dead-letter queue
        }
    }

    private static String subjectFor(OrderStatusChangedEvent e) {
        String short_ = e.orderId().toString().substring(0, 8);
        return switch (e.newStatus()) {
            case PENDING -> "We received your order #" + short_;
            case CONFIRMED -> "Order #" + short_ + " is confirmed";
            case SHIPPING -> "Order #" + short_ + " is on its way";
            case COMPLETED -> "Order #" + short_ + " has been delivered";
            case CANCELLED -> "Order #" + short_ + " was cancelled";
        };
    }

    private static String bodyFor(OrderStatusChangedEvent e) {
        String from = e.oldStatus() == null ? "new" : e.oldStatus().name();
        String extra = e.newStatus() == OrderStatus.PENDING ? "\nPlease complete the payment within 15 minutes." : "";
        return "Order " + e.orderId() + " changed status: " + from + " -> " + e.newStatus() + "." + extra;
    }
}
