package com.p.logistics.inventory.service;

import com.p.logistics.inventory.dto.ReserveInventoryRequest;
import com.p.logistics.inventory.entity.InventoryItem;
import com.p.logistics.inventory.exception.InsufficientStockException;
import com.p.logistics.inventory.repository.JpaInventoryItemRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public ReserveResult batchReserve(List<ReserveInventoryRequest> requestList) {
        Map<String, Integer> requestItems = new HashMap<>();
        requestList.forEach(request -> {
            requestItems.put(request.productId(), requestItems.getOrDefault(request.productId(), 0) + request.quantity());
        });

        return inventoryTransactionService.batchReserve(requestItems);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = ALLOWED_REATTEMPTS_NUMBER)
    public boolean reserve(String productId, int quantity) {
        return inventoryTransactionService.reserve(productId, quantity);
    }
}
