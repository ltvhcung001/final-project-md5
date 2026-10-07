package com.omnichannel.order.config;

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
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RabbitConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }

    @Bean
    Queue paymentResultQueue() {
        return QueueBuilder.durable(EventNames.QUEUE_ORDER_PAYMENT_RESULT).build();
    }

    @Bean
    Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentResultQueue()).to(eventsExchange()).with(EventNames.PAYMENT_SUCCEEDED);
    }

    @Bean
    Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentResultQueue()).to(eventsExchange()).with(EventNames.PAYMENT_FAILED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
