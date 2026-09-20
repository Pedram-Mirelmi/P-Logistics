package com.p.logistics.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryTransactionService {

    private final JpaInventoryItemRepository inventoryRepository;
    private final JpaInventoryReservationRepository reservationRepository;

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
}
