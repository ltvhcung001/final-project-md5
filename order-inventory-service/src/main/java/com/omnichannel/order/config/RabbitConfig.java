package com.omnichannel.order.config;

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
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RabbitConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }

    @Bean
    Queue paymentSucceededQueue() {
        return withDeadLetter(EventNames.QUEUE_ORDER_PAYMENT_SUCCEEDED);
    }

    @Bean
    Queue paymentFailedQueue() {
        return withDeadLetter(EventNames.QUEUE_ORDER_PAYMENT_FAILED);
    }

    @Bean
    Queue paymentSucceededDlq() {
        return QueueBuilder.durable(EventNames.QUEUE_ORDER_PAYMENT_SUCCEEDED + ".dlq").build();
    }

    @Bean
    Queue paymentFailedDlq() {
        return QueueBuilder.durable(EventNames.QUEUE_ORDER_PAYMENT_FAILED + ".dlq").build();
    }

    @Bean
    Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentSucceededQueue()).to(eventsExchange()).with(EventNames.PAYMENT_SUCCEEDED);
    }

    @Bean
    Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQueue()).to(eventsExchange()).with(EventNames.PAYMENT_FAILED);
    }

    /** Event types are inferred from the listener method, so producers need not send Java class headers. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.omnichannel.common.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    private static Queue withDeadLetter(String name) {
        return QueueBuilder.durable(name).deadLetterExchange("").deadLetterRoutingKey(name + ".dlq").build();
    }
}
