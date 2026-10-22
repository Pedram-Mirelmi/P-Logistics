package com.p.logistics.inventory.reservation.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "reservation_requests")
@NoArgsConstructor
public class ReservationRequest {

    public ReservationRequest(String orderId, String productId, int quantity, ReservationOutcome status) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reservation_requests_seq")
    @SequenceGenerator(name = "reservation_requests_seq", sequenceName = "reservation_requests_seq", allocationSize = 50)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "status", nullable = false, length = 15)
    private ReservationOutcome status;
}
