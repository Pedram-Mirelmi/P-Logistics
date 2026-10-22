package com.p.logistics.order.service;

import com.p.logistics.order.client.InventoryClient;
import com.p.logistics.order.client.InventoryUnavailableException;
import com.p.logistics.order.client.ReservationOutcome;
import com.p.logistics.order.dto.ReserveInventoryRequest;
import com.p.logistics.order.entity.Order;
import com.p.logistics.order.entity.OrderLine;
import com.p.logistics.order.entity.OrderStatus;
import com.p.logistics.order.repository.JpaOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final JpaOrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final OutboxEventRecorder outboxEventRecorder;

    public OrderService(
            JpaOrderRepository orderRepository, InventoryClient inventoryClient, OutboxEventRecorder outboxEventRecorder) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.outboxEventRecorder = outboxEventRecorder;
    }

    @Transactional
    public Order submitOrder(List<OrderLine> lines) {

        List<ReserveInventoryRequest> reservationRequest = lines
                .stream()
                .map(line -> new ReserveInventoryRequest(line.getProductId(), line.getQuantity()))
                .toList();

        boolean allReserved;
        try {
            allReserved = inventoryClient.batchReserve(reservationRequest) == ReservationOutcome.RESERVED;
        } catch (InventoryUnavailableException e) {
            // Fail-closed placeholder: treat "couldn't tell" the same as
            // "no stock" until the failure experiments establish a
            // deliberate retry/timeout policy for this call.
            allReserved = false;
        }

        OrderStatus status =
                allReserved ? OrderStatus.ACCEPTED : OrderStatus.REJECTED;

        Order order = new Order(
                UUID.randomUUID(),
                lines,
                status
        );

        orderRepository.save(order);
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            // Same local transaction as the order insert above (see
            // OutboxEventRecorder) - shipment creation itself now happens
            // later, asynchronously, once OutboxRelay publishes this event
            // and OrderAcceptedListener picks it up.
            outboxEventRecorder.recordOrderAccepted(order.getId(), "DEFAULT_DESTINATION");
        }

        return order;
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Order not found"));
    }
}
