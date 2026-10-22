package com.p.logistics.inventory.reservation.repository;

import com.p.logistics.inventory.reservation.entity.ReservationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemReservationRequestRepository extends JpaRepository<ReservationRequest, Long> {
    boolean existsByOrderId(String orderId);
}
