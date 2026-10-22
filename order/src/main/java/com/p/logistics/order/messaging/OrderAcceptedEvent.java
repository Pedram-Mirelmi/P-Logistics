package com.p.logistics.order.messaging;

import java.util.UUID;

// The message contract published when an order is accepted. eventId is the
// correlation id: it's the same value as the OutboxEvent row's id, carried
// through as the AMQP message's correlationId header too, so one order's
// journey can be followed across the outbox table, RabbitMQ, and the
// consumer's logs. schemaVersion exists because the producer (this relay)
// and the consumer (OrderAcceptedListener) can end up on different
// deployments over time - a consumer reading an unexpected version can
// reject cleanly instead of guessing at a payload shape it wasn't built for.
public record OrderAcceptedEvent(
        UUID eventId,
        int schemaVersion,
        UUID orderId,
        String destination
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static OrderAcceptedEvent of(UUID eventId, UUID orderId, String destination) {
        return new OrderAcceptedEvent(eventId, CURRENT_SCHEMA_VERSION, orderId, destination);
    }
}
