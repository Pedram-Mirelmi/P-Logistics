package com.p.logistics.inventory.messaging.repository;

import com.p.logistics.inventory.messaging.entity.ReservationResponseEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationResponseEventRepository extends JpaRepository<ReservationResponseEventEntity, UUID> {
    List<ReservationResponseEventEntity> findByStatus(ReservationResponseEventEntity.OutboxEventStatus outboxEventStatus);


    @Query(value = """
            SELECT * FROM reservation_response_events
            WHERE last_attempt < :attr0 AND status = 'ATTEMPTED'
            FOR UPDATE SKIP LOCKED
    """,
    nativeQuery = true)
    List<ReservationResponseEventEntity> findAllByLastAttemptBeforeForUpdate(Instant attr0);

    List<ReservationResponseEventEntity> findAllByStatus(ReservationResponseEventEntity.OutboxEventStatus status);

    @Query(value = """
        SELECT * FROM reservation_response_events
        WHERE status = :outboxEventStatus
    """,
    nativeQuery = true)
    List<ReservationResponseEventEntity> findAllByStatusForUpdate(ReservationResponseEventEntity.OutboxEventStatus outboxEventStatus);
}
