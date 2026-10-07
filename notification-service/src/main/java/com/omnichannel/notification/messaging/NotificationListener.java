package com.omnichannel.notification.messaging;

import com.omnichannel.common.event.EventNames;
import com.omnichannel.common.event.OrderStatusChangedEvent;
import com.omnichannel.notification.document.DeliveryLog;
import com.omnichannel.notification.repository.DeliveryLogRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

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
        String subject = "Order " + event.orderId() + " is now " + event.newStatus();
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(event.userEmail());
            mail.setSubject(subject);
            mail.setText("Your order status changed from " + event.oldStatus() + " to " + event.newStatus() + ".");
            mailSender.send(mail);
            logs.save(new DeliveryLog(eventId, event.userEmail(), subject, DeliveryLog.Status.SENT, null));
        } catch (Exception ex) {
            logs.save(new DeliveryLog(eventId, event.userEmail(), subject, DeliveryLog.Status.FAILED, ex.getMessage()));
            throw ex; // let RabbitMQ retry / dead-letter
        }
    }
}
