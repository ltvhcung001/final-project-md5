package com.omnichannel.notification.config;

import com.omnichannel.common.event.EventNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }

    /** Durable queue: while this service is down, messages accumulate and are delivered on restart. */
    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(EventNames.QUEUE_NOTIFICATION).build();
    }

    @Bean
    Binding statusChangedBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventsExchange()).with(EventNames.ORDER_STATUS_CHANGED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
