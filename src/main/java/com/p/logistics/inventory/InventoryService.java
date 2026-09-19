package com.p.logistics.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {

    private final JpaInventoryItemRepository inventoryRepository;

    public InventoryService(JpaInventoryItemRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public InventoryItem getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found"));
    }

    @Transactional
    public boolean reserve(String productId, int quantity) {
        Optional<InventoryItem> inventoryItem = inventoryRepository.findById(productId);
        return inventoryItem.map(
                item -> item.reserve(quantity)
        ).orElse(false);
    }
}