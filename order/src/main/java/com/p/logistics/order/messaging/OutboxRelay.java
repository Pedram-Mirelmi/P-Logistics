package com.p.logistics.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p.logistics.order.entity.OutboxEvent;
import com.p.logistics.order.repository.JpaOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Slf4j
@Component
public class OutboxRelay {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final JpaOutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(
            JpaOutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxEventStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        List<InFlight> inFlight = new ArrayList<>(pending.size());
        for (OutboxEvent event : pending) {
            try {
                inFlight.add(new InFlight(event, publish(event)));
            } catch (RuntimeException e) {
                log.error("Could not publish outbox event {}; retrying next tick", event.getId(), e);
            }
        }

        long deadline = System.nanoTime() + CONFIRM_TIMEOUT.toNanos();
        for (InFlight sent : inFlight) {
            if (isConfirmed(sent, deadline)) {
                outboxEventRepository.markPublished(sent.event().getId(), Instant.now());
            }
        }
    }

    private CorrelationData publish(OutboxEvent event) {
        OrderAcceptedEvent payload;
        try {
            payload = objectMapper.readValue(event.getPayload(), OrderAcceptedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox event " + event.getId() + " has an unreadable payload", e);
        }

        log.info("Publishing outbox event {} (order {})", event.getId(), event.getAggregateId());

        // The broker's confirm (and the returned message, if unroutable) is
        // delivered to this object, matched back by the outbox event's id.
        CorrelationData correlation = new CorrelationData(event.getId().toString());
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_EVENTS_EXCHANGE,
                RabbitMqConfig.ORDER_ACCEPTED_ROUTING_KEY,
                payload,
                message -> {
                    message.getMessageProperties().setCorrelationId(event.getId().toString());
                    return message;
                },
                correlation);
        return correlation;
    }

    // Spring AMQP sets the returned message on the CorrelationData before it
    // completes the confirm future, so checking getReturned() after the
    // confirm arrives is reliable.
    private boolean isConfirmed(InFlight sent, long deadlineNanos) {
        CorrelationData correlation = sent.correlation();
        try {
            long remaining = Math.max(0, deadlineNanos - System.nanoTime());
            CorrelationData.Confirm confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);

            if (!confirm.isAck()) {
                log.warn("Broker nacked outbox event {}: {}", correlation.getId(), confirm.getReason());
                return false;
            }
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                log.warn("Outbox event {} was unroutable ({} {}, exchange={}, routingKey={})",
                        correlation.getId(), returned.getReplyCode(), returned.getReplyText(),
                        returned.getExchange(), returned.getRoutingKey());
                return false;
            }
            return true;
        } catch (TimeoutException e) {
            log.warn("No broker confirm for outbox event {} within {}", correlation.getId(), CONFIRM_TIMEOUT);
            return false;
        } catch (ExecutionException e) {
            log.warn("Confirm failed for outbox event {}", correlation.getId(), e.getCause());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record InFlight(OutboxEvent event, CorrelationData correlation) {
    }
}
