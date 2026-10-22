package com.p.logistics.inventory.reservation.repository;

import com.p.logistics.inventory.reservation.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    List<InventoryReservation> findByProductId(String productId);
}
