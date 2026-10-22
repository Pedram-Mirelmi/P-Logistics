package com.p.logistics.inventory.inventory_item.controller;

import com.p.logistics.inventory.inventory_item.dto.OrderItem;
import com.p.logistics.inventory.inventory_item.dto.ReserveInventoryResponse;
import com.p.logistics.inventory.inventory_item.entity.InventoryItem;
import com.p.logistics.inventory.inventory_item.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItem> getInventory(
            @PathVariable String productId
    ) {
        return ResponseEntity.ok(
                inventoryService.getInventory(productId)
        );
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReserveInventoryResponse> reserve(
            @RequestBody OrderItem request
    ) {
        boolean reserved = inventoryService.reserve(request.productId(), request.quantity());
        return ResponseEntity.ok(new ReserveInventoryResponse(reserved));
    }

    @PostMapping("/batch-reserve")
    public ResponseEntity<Boolean> batchReserve(
            @RequestBody List<OrderItem> requestList
    ) {
        try {
            return switch (inventoryService.batchReserve(requestList)) {
                case INSUFFICIENT_STOCK -> ResponseEntity.ok(false);
                case SUCCESS -> ResponseEntity.ok(true);
                case UNKNOWN_PRODUCT_ID -> ResponseEntity.notFound().build();
            };
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}