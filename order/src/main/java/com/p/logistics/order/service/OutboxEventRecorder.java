package com.p.logistics.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p.logistics.order.entity.OutboxEvent;
import com.p.logistics.order.messaging.OrderAcceptedEvent;
import com.p.logistics.order.repository.JpaOutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Called from inside OrderService.submitOrder()'s existing @Transactional
// method, so this insert shares that same local transaction/connection as
// the order insert - "the order was accepted" and "there is a record that
// needs telling to the outside world" commit or roll back together. Nothing
// here talks to RabbitMQ; that's OutboxRelay's job, running later,
// separately, off this table.
@Service
public class OutboxEventRecorder {

    private static final String ORDER_ACCEPTED_EVENT_TYPE = "OrderAccepted";

    private final JpaOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventRecorder(JpaOutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void recordOrderAccepted(UUID orderId, String destination) {
        UUID eventId = UUID.randomUUID();
        OrderAcceptedEvent event = OrderAcceptedEvent.of(eventId, orderId, destination);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + event, e);
        }

        outboxEventRepository.save(new OutboxEvent(eventId, orderId, ORDER_ACCEPTED_EVENT_TYPE, payload));
    }
}
