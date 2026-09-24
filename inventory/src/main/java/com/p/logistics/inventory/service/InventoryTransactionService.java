package com.p.logistics.inventory.service;

import com.p.logistics.inventory.entity.InventoryItem;
import com.p.logistics.inventory.entity.InventoryReservation;
import com.p.logistics.inventory.repository.JpaInventoryItemRepository;
import com.p.logistics.inventory.repository.JpaInventoryReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryTransactionService {

    private final JpaInventoryItemRepository inventoryRepository;
    private final JpaInventoryReservationRepository reservationRepository;
    @PersistenceContext
    private EntityManager entityManager;

    public InventoryTransactionService(JpaInventoryItemRepository inventoryRepository,
                                        JpaInventoryReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public boolean reserve(String productId, int quantity) {
        Optional<InventoryItem> inventoryItem = inventoryRepository.findById(productId);
        boolean reserved = inventoryItem.map(item -> item.reserve(quantity)).orElse(false);

        if (reserved) {
            reservationRepository.save(
                    new InventoryReservation(UUID.randomUUID().toString(), productId, quantity));
        }

        return reserved;
    }

    @Transactional
    public ReserveResult batchReserve(Map<String, Integer> requestItems) {
        List<InventoryItem> items = inventoryRepository.findAllByIdIn(requestItems.keySet());
        if(items.size() != requestItems.size()) {
            return ReserveResult.UNKNOWN_PRODUCT_ID;
        }
        if(items.stream().anyMatch(item -> !item.isAvailable(requestItems.get(item.getProductId())))) {
            return ReserveResult.INSUFFICIENT_STOCK;
        }

        items.forEach(item -> item.reserve(requestItems.get(item.getProductId())));
        inventoryRepository.flush();


        requestItems.forEach((productId, quantity) -> entityManager.persist(new InventoryReservation(UUID.randomUUID().toString(), productId, quantity)));

        return ReserveResult.SUCCESS;
    }
}
