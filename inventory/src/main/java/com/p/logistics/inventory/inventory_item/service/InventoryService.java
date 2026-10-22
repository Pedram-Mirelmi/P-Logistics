package com.p.logistics.inventory.inventory_item.service;

import com.p.logistics.inventory.inventory_item.dto.OrderItem;
import com.p.logistics.inventory.inventory_item.entity.InventoryItem;
import com.p.logistics.inventory.reservation.entity.InventoryReservation;
import com.p.logistics.inventory.reservation.entity.ReservationOutcome;
import com.p.logistics.inventory.reservation.entity.ReservationRequest;
import com.p.logistics.inventory.inventory_item.repository.InventoryItemRepository;
import com.p.logistics.inventory.reservation.repository.InventoryReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class InventoryService {

    private static final int ALLOWED_REATTEMPTS_NUMBER = 3;

    private final InventoryItemRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryService self;

    @PersistenceContext
    private EntityManager entityManager;

    public InventoryService(InventoryItemRepository inventoryRepository, InventoryReservationRepository reservationRepository, InventoryService self) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.self = self;
    }

    public InventoryItem getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found"));
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = ALLOWED_REATTEMPTS_NUMBER)
    public ReservationOutcome batchReserve(List<OrderItem> requestList) {
        Map<String, Integer> requestItems = new HashMap<>();
        requestList.forEach(request -> {
            requestItems.put(request.productId(), requestItems.getOrDefault(request.productId(), 0) + request.quantity());
        });

        return self.attemptBatchReserve(requestItems);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = ALLOWED_REATTEMPTS_NUMBER)
    public boolean reserve(String productId, int quantity) {
        return self.attemptReserve(productId, quantity);
    }

    @Transactional
    public void persistReservationRequests(List<OrderItem> orderItems, String orderId) {
        orderItems
                .stream()
                .map(
                        item -> new ReservationRequest(orderId, item.productId(), item.quantity(), ReservationRequest.ReservationRequestStatus.PENDING)
                )
                .forEach(entityManager::persist);
    }

    @Transactional
    public boolean attemptReserve(String productId, int quantity) {
        Optional<InventoryItem> inventoryItem = inventoryRepository.findById(productId);
        boolean reserved = inventoryItem.map(item -> item.reserve(quantity)).orElse(false);

        if (reserved) {
            reservationRepository.save(
                    new InventoryReservation(UUID.randomUUID().toString(), productId, quantity));
        }

        return reserved;
    }

    @Transactional
    public ReservationOutcome attemptBatchReserve(Map<String, Integer> requestItems) {
        List<InventoryItem> items = inventoryRepository.findAllByIdIn(requestItems.keySet());
        if(items.size() != requestItems.size()) {
            return ReservationOutcome.UNKNOWN_PRODUCT_ID;
        }
        if(items.stream().anyMatch(item -> !item.isAvailable(requestItems.get(item.getProductId())))) {
            return ReservationOutcome.INSUFFICIENT_STOCK;
        }

        items.forEach(item -> item.reserve(requestItems.get(item.getProductId())));
        inventoryRepository.flush();


        requestItems.forEach((productId, quantity) -> entityManager.persist(new InventoryReservation(UUID.randomUUID().toString(), productId, quantity)));

        return ReservationOutcome.SUCCESS;
    }
}
