package com.p.logistics.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaInventoryItemRepository extends JpaRepository<InventoryItem, String> {

    Optional<InventoryItem> findByProductId(String productId);
}
