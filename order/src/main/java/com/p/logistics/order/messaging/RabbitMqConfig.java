package com.p.logistics.order.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

// Step 07 (Chapter 7: Message-Oriented Middleware): the RabbitMQ topology
// for the OrderAccepted flow, plus the retry/dead-letter policy for
// consuming it.
//
//   OutboxRelay --publish--> [order.events exchange] --"order.accepted"-->
//       [shipment.creation.queue] --> OrderAcceptedListener
//
// If the listener keeps throwing for a message, retryListenerContainerFactory
// redelivers it a few times locally (with backoff) before giving up and
// rejecting it without requeueing; because shipment.creation.queue is
// declared with a dead-letter-exchange argument, RabbitMQ itself then routes
// that rejected message to order.events.dlx, which lands it in
// shipment.creation.dlq - a poison message ends up quarantined there
// instead of blocking every message behind it forever.
@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_ACCEPTED_ROUTING_KEY = "order.accepted";
    public static final String SHIPMENT_CREATION_QUEUE = "shipment.creation.queue";

    private static final String DEAD_LETTER_EXCHANGE = "order.events.dlx";
    private static final String SHIPMENT_CREATION_DLQ = "shipment.creation.dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue shipmentCreationQueue() {
        return QueueBuilder.durable(SHIPMENT_CREATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_ACCEPTED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding shipmentCreationBinding(Queue shipmentCreationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(shipmentCreationQueue)
                .to(orderEventsExchange)
                .with(ORDER_ACCEPTED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange orderEventsDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue shipmentCreationDeadLetterQueue() {
        return QueueBuilder.durable(SHIPMENT_CREATION_DLQ).build();
    }

    @Bean
    public Binding shipmentCreationDeadLetterBinding(
            Queue shipmentCreationDeadLetterQueue, TopicExchange orderEventsDeadLetterExchange) {
        return BindingBuilder.bind(shipmentCreationDeadLetterQueue)
                .to(orderEventsDeadLetterExchange)
                .with(ORDER_ACCEPTED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        // Unroutable messages come back to the publisher (attached to the
        // CorrelationData, see OutboxRelay) instead of being silently
        // dropped - a confirm alone only says the broker accepted it.
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    // 3 local attempts (1s, then 2s backoff between them) for transient
    // failures - e.g. Postgres hiccuping - before giving up on a message
    // and letting it fall through to the DLQ above.
    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setContainerCustomizer(container -> container.setAdviceChain(retryInterceptor));
        return factory;
    }
}
