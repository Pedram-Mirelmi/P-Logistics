package com.p.logistics.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaInventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    List<InventoryReservation> findByProductId(String productId);
}
