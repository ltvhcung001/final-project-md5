package com.omnichannel.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.common.event.EventNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
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

    /**
     * Durable queue: while this service is down, messages accumulate and are delivered on restart, so
     * order placement is never affected by a notification outage. Poison messages end up in the DLQ.
     */
    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(EventNames.QUEUE_NOTIFICATION)
                .deadLetterExchange("")
                .deadLetterRoutingKey(EventNames.QUEUE_NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(EventNames.QUEUE_NOTIFICATION_DLQ).build();
    }

    @Bean
    Binding statusChangedBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventsExchange()).with(EventNames.ORDER_STATUS_CHANGED);
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.omnichannel.common.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
