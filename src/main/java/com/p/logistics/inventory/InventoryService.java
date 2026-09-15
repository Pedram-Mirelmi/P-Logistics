package com.p.logistics.inventory;

import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public InventoryItem getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found"));
    }

    public boolean reserve(String productId, int quantity) {
        return inventoryRepository.reserve(productId, quantity);
    }
}