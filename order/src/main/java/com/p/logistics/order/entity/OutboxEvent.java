package com.p.logistics.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// One row = one business fact that happened, e.g. "order X was accepted".
// Written in the same local transaction as the change it describes (see
// OrderService.submitOrder) so the fact and the change commit or roll back
// together - see V6__outbox_event.sql. OutboxRelay is the only thing that
// reads PENDING rows and is responsible for getting them to RabbitMQ.
@Getter
@Setter
@Entity
@Table(name = "outbox_event")
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    private UUID aggregateId;

    private String eventType;

    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    private Instant createdAt;

    private Instant publishedAt;

    public OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public enum OutboxEventStatus {
        PENDING,
        PUBLISHED
    }
}
