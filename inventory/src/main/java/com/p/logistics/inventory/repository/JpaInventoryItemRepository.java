package com.p.logistics.inventory.repository;

import com.p.logistics.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface JpaInventoryItemRepository extends JpaRepository<InventoryItem, String> {

    Optional<InventoryItem> findByProductId(String productId);

    // Bulk update: runs as raw SQL against the table without loading the
    // entity, so it never touches the @Version column and bypasses
    // optimistic locking entirely. Exists only to demonstrate, in
    // Experiment C, what "no concurrency protection" looks like.
    @Modifying
    @Query("UPDATE InventoryItem i SET i.quantity = :quantity WHERE i.productId = :productId")
    int updateQuantityBlind(@Param("productId") String productId, @Param("quantity") int quantity);

    // Issues SELECT ... FOR UPDATE: takes a DB row lock held for the rest of
    // the transaction, so a second transaction calling this for the same row
    // blocks until the first one commits or rolls back.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productId = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") String productId);

    @Query("SELECT i FROM InventoryItem i WHERE i.productId IN :entries")
    List<InventoryItem> findAllByIdIn(Set<String> entries);
}
