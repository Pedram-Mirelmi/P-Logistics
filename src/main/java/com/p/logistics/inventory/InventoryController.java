package com.p.logistics.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}