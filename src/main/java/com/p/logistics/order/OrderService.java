package com.p.logistics.order;

import com.p.logistics.inventory.InventoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public OrderService(
            OrderRepository orderRepository,
            InventoryRepository inventoryRepository
    ) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public Order submitOrder(List<OrderLine> lines) {

        boolean allReserved = true;

        for (OrderLine line : lines) {
            boolean reserved = inventoryRepository.reserve(
                    line.productId(),
                    line.quantity()
            );

            if (!reserved) {
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

        return order;
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Order not found"));
    }
}
