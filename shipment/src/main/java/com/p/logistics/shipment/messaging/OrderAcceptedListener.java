package com.p.logistics.shipment.messaging;

import com.p.logistics.shipment.messaging.RabbitMqConfig;
import com.p.logistics.shipment.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Consumes OrderAccepted off shipment.creation.queue and creates the
// shipment - this replaces the direct shipmentService.createShipment(...)
// call OrderService.submitOrder used to make inline before Step 07.
//
// Idempotent by construction: if a shipment already exists for this order
// id, do nothing instead of creating a second one. That check earns its
// keep because this method really can run more than once for the same
// event - RabbitMQ redelivers a message if the container crashes before
// acknowledging it, and OutboxRelay can publish the same outbox row twice
// if it crashes between publishing and marking that row PUBLISHED (see
// OutboxRelay). The delivery guarantee underneath this is "at-least-once",
// never "exactly-once" - this check is what makes the observable effect
// (one shipment per order) exactly-once anyway.
//
// Uses the default (AUTO) acknowledge mode: the container only sends the
// ack to RabbitMQ after this method returns without throwing, and
// nacks/retries (see RabbitMqConfig's retryInterceptor) if it throws. That
// already gives "redeliver on crash-before-ack" without hand-written
// Channel.basicAck plumbing.
@Slf4j
@Component
public class OrderAcceptedListener {

    private final ShipmentService shipmentService;

    public OrderAcceptedListener(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @RabbitListener(queues = RabbitMqConfig.SHIPMENT_CREATION_QUEUE)
    public void onOrderAccepted(OrderAcceptedEvent event) {
        log.info("Received OrderAccepted {} for order {}", event.eventId(), event.orderId());

        if (shipmentService.findShipmentByOrderId(event.orderId()).isPresent()) {
            log.info("Shipment for order {} already exists - skipping duplicate of event {}",
                    event.orderId(), event.eventId());
            return;
        }
        shipmentService.createShipment(event.orderId(), event.destination());
    }
}
