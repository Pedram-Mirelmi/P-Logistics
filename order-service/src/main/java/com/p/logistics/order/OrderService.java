package com.p.logistics.order;

import com.p.logistics.inventory.InventoryClient;
import com.p.logistics.inventory.InventoryUnavailableException;
import com.p.logistics.shipment.ShipmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final JpaOrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final ShipmentService shipmentService;

    public OrderService(JpaOrderRepository orderRepository, InventoryClient inventoryClient, ShipmentService shipmentService) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.shipmentService = shipmentService;
    }

    @Transactional
    public Order submitOrder(List<OrderLine> lines) {

        boolean allReserved = true;

        for (OrderLine line : lines) {
            boolean reserved;
            try {
                reserved = inventoryClient.reserve(
                        line.getProductId(),
                        line.getQuantity()
                );
                if (!reserved) {
                    allReserved = false;
                    break;
                }
            } catch (InventoryUnavailableException e) {
                // Fail-closed placeholder: treat "couldn't tell" the same as
                // "no stock" until the failure experiments establish a
                // deliberate retry/timeout policy for this call.
                allReserved = false;
                break;
            }
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
            shipmentService.createShipment(order.getId(), "DEFAULT_DESTINATION");
        }

        return order;
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Order not found"));
    }
}
