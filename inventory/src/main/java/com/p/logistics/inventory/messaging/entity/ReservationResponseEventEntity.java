package com.p.logistics.inventory.messaging.entity;

import com.p.logistics.inventory.reservation.entity.ReservationOutcome;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@Entity
@Table(name = "reservation_response_events")
@NoArgsConstructor
public class ReservationResponseEventEntity {

    @Id
    private UUID id;

    @Column(unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private ReservationOutcome outcome;

    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    private Instant createdAt;

    @Column(name = "last_attempt")
    private Instant lastAttempt;


    public ReservationResponseEventEntity(UUID id, ReservationOutcome outcome) {
        this.id = id;
        this.status = OutboxEventStatus.WAITING;
        this.createdAt = Instant.now();
        this.outcome = outcome;
    }

    public enum OutboxEventStatus {
        WAITING,
        ATTEMPTED,
        CONFIRMED
    }
}
