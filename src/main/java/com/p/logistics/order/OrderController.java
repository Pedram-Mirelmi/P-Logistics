package com.p.logistics.order;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> submitOrder(
            @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.submitOrder(request.lines());

        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                orderService.getOrder(id)
        );
    }
}
