package com.p.logistics.order.repository;

import com.p.logistics.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxEventStatus status);

    // A single UPDATE instead of loading the entity and letting dirty-checking
    // flush it. OutboxRelay runs outside any transaction, so this method
    // opens and commits its own - one short transaction per published row.
    @Modifying
    @Transactional
    @Query("update OutboxEvent e set e.status = com.p.logistics.order.entity.OutboxStatus.PUBLISHED, e.publishedAt = :publishedAt where e.id = :id")
    void markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}
