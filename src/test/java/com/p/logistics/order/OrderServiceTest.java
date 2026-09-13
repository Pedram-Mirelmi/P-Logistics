package com.p.logistics.order;


import com.p.logistics.inventory.InMemoryInventoryRepository;
import com.p.logistics.inventory.InventoryItem;
import com.p.logistics.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    @Test
    void rejectedOrderLeavesPartialReservation() {
        OrderRepository orderRepository = new InMemoryOrderRepository();
        InventoryRepository inventoryRepository =
                new InMemoryInventoryRepository();

        inventoryRepository.save(new InventoryItem("P1", 10));
        inventoryRepository.save(new InventoryItem("P2", 5));

        OrderService orderService =
                new OrderService(orderRepository, inventoryRepository);

        Order order = orderService.submitOrder(
                List.of(
                        new OrderLine("P1", 2),
                        new OrderLine("P2", 100)
                )
        );

        assertEquals(OrderStatus.REJECTED, order.getStatus());

        InventoryItem p1 = inventoryRepository
                .findByProductId("P1")
                .orElseThrow();

        assertEquals(8, p1.getAvailableQuantity());
    }
}
