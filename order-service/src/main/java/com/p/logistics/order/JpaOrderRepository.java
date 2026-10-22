package com.p.logistics.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaOrderRepository extends JpaRepository<Order, UUID> {

    // Fetch join: pulls each order's lines in the same query instead of one
    // lazy-load query per order (fixes the N+1 that plain findAll() causes
    // once callers touch order.getLines()).
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.lines")
    List<Order> findAllWithLines();
}
