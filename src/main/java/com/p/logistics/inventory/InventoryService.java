package com.p.logistics.inventory;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class InventoryService {

    private static final int ALLOWED_REATTEMPTS_NUMBER = 3;

    private final JpaInventoryItemRepository inventoryRepository;
    private final TransactionTemplate transactionTemplate;

    public InventoryService(JpaInventoryItemRepository inventoryRepository, PlatformTransactionManager transactionManager) {
        this.inventoryRepository = inventoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public InventoryItem getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found"));
    }


    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class)
    @Transactional
    public boolean reserve(String productId, int quantity) {
        Optional<InventoryItem> inventoryItem = inventoryRepository.findById(productId);
        return inventoryItem.map(item -> item.reserve(quantity)).orElse(false);
    }

}