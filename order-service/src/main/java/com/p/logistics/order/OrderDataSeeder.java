package com.p.logistics.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Runs once on startup against an empty table. Goes through OrderService
// (not the repository directly), so it exercises the real RPC call to
// inventory-service - product ids match InventoryDataSeeder's seeded
// catalog, so the reservation succeeds and a shipment gets created too.
@Slf4j
@Component
public class OrderDataSeeder implements CommandLineRunner {

    private final JpaOrderRepository orderRepository;
    private final OrderService orderService;

    public OrderDataSeeder(JpaOrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Override
    public void run(String... args) {
        if (orderRepository.count() > 0) {
            return;
        }

        Order order = orderService.submitOrder(List.of(
                new OrderLine("SKU-1", 5)
        ));

        log.info("Seeded demo order {} with status {}", order.getId(), order.getStatus());
    }
}
