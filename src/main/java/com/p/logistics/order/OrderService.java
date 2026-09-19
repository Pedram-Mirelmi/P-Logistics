package com.p.logistics.order;

import com.p.logistics.inventory.InventoryService;
import com.p.logistics.shipment.ShipmentService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final JpaOrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ShipmentService shipmentService;

    public OrderService(JpaOrderRepository orderRepository, InventoryService inventoryService, ShipmentService shipmentService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.shipmentService = shipmentService;
    }

    @Transactional
    public Order submitOrder(List<OrderLine> lines) {

        boolean allReserved = true;

        for (OrderLine line : lines) {
            boolean reserved;
            try {
                reserved = inventoryService.reserve(
                        line.getProductId(),
                        line.getQuantity()
                );
                if (!reserved) {
                    allReserved = false;
                    break;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
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
