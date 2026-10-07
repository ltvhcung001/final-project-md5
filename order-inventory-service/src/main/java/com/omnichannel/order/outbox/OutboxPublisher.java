package com.omnichannel.order.outbox;

import com.omnichannel.common.event.EventNames;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
public class OutboxPublisher {

    private final OutboxRepository repo;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(OutboxRepository repo, RabbitTemplate rabbit) {
        this.repo = repo;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${order.outbox-poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent e : repo.findUnpublished(PageRequest.of(0, 100))) {
            var message = MessageBuilder
                    .withBody(e.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(e.getId().toString())
                    .build();
            rabbit.send(EventNames.EXCHANGE, e.getRoutingKey(), message);
            e.markPublished();
        }
    }
}
