package com.omnichannel.payment.config;

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

    @Bean
    Queue orderCreatedQueue() {
        return QueueBuilder.durable(EventNames.QUEUE_PAYMENT_ORDER_CREATED).build();
    }

    @Bean
    Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(eventsExchange()).with(EventNames.ORDER_CREATED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
