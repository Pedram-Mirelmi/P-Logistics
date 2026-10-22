package com.p.logistics.inventory.messaging.mq;

import org.springframework.amqp.core.*;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    // INCOMING
    public static final String ORDER_REGISTERED_QUEUE = "order-registered-queue";


    // OUTGOING
    public static final String RESERVATION_OUTCOME_ROUTING_KEY = "reservation-outcome-queue";


    private static final String DEAD_LETTER_EXCHANGE = "dlx";
    private static final String DEAD_LETTER_QUEUE = "dlq";


    @Bean
    public Queue orderRegisteredQueue() {
        return QueueBuilder
                .durable(ORDER_REGISTERED_QUEUE)
                .build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with("*");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
