package com.p.logistics.inventory;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private static final int ALLOWED_REATTEMPTS_NUMBER = 3;

    private final JpaInventoryItemRepository inventoryRepository;
    private final InventoryTransactionService inventoryTransactionService;

    public InventoryService(JpaInventoryItemRepository inventoryRepository,
                             InventoryTransactionService inventoryTransactionService) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionService = inventoryTransactionService;
    }

    public InventoryItem getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found"));
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = ALLOWED_REATTEMPTS_NUMBER)
    public boolean reserve(String productId, int quantity) {
        return inventoryTransactionService.reserve(productId, quantity);
    }
}
