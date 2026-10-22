package com.p.logistics.inventory.messaging.service;

import com.p.logistics.inventory.messaging.entity.ReservationResponseEventEntity;
import com.p.logistics.inventory.messaging.event.ReservationResponseEvent;
import com.p.logistics.inventory.messaging.mq.RabbitMqConfig;
import com.p.logistics.inventory.messaging.repository.ReservationResponseEventRepository;
import com.p.logistics.inventory.reservation.entity.ReservationOutcome;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OutBoxService {

    private final ConcurrentHashMap<UUID, CorrelationData> correlationDataMap = new ConcurrentHashMap<>();

    private static final long RETRY_TIMEOUT = 30000L;

    private final OutBoxService self;
    private final ReservationResponseEventRepository reservationResponseEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutBoxService(OutBoxService self, ReservationResponseEventRepository reservationResponseEventRepository, RabbitTemplate rabbitTemplate) {
        this.self = self;
        this.reservationResponseEventRepository = reservationResponseEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void saveReservationOutcomeToPublish(String orderId, ReservationOutcome outcome) {
        ReservationResponseEventEntity event = new ReservationResponseEventEntity(UUID.randomUUID(), outcome);
        reservationResponseEventRepository.save(event);
    }

    @Scheduled
    public void relayEvents() {

        self.checkForConfirmations();

        self.relayWaitingEvents();

        self.retryTimedOutEvents();
    }

    @Transactional
    public void retryTimedOutEvents() {
        List<ReservationResponseEventEntity> timedOutEvents = reservationResponseEventRepository.findAllByLastAttemptBeforeForUpdate((Instant.now().minusMillis(RETRY_TIMEOUT)));

        for (var event : timedOutEvents) {
            CorrelationData correlation = new CorrelationData(event.getOrderId().toString());

            rabbitTemplate.convertAndSend(
                    "",
                    RabbitMqConfig.RESERVATION_OUTCOME_ROUTING_KEY,
                    new ReservationResponseEvent(event.getOrderId(), event.getOutcome()),
                    correlation
            );
            correlationDataMap.put(event.getOrderId(), correlation);
            event.setLastAttempt(Instant.now());
        }
    }

    @Transactional
    public void checkForConfirmations() {
        List<ReservationResponseEventEntity> attemptedEvents = reservationResponseEventRepository.findAllByStatusForUpdate((ReservationResponseEventEntity.OutboxEventStatus.ATTEMPTED));
        for (var event : attemptedEvents) {
            CorrelationData correlationData = correlationDataMap.getOrDefault(event.getOrderId(), null);
            if (correlationData != null) {
                CorrelationData.Confirm confirm = correlationData.getFuture().getNow(null);

                if(confirm.isAck()) {
                    if (correlationData.getReturned() == null) {
                        event.setStatus(ReservationResponseEventEntity.OutboxEventStatus.CONFIRMED);
                        correlationDataMap.remove(event.getOrderId());
                    } else {
                        log.error("Correlation data returned: {}", correlationData.getReturned());
                    }
                } else {
                    log.error("Correlation data not confirmed: {}", correlationData);
                }
            } else {
                correlationData = new CorrelationData(event.getOrderId().toString());
                rabbitTemplate.convertAndSend(
                        "",
                        RabbitMqConfig.RESERVATION_OUTCOME_ROUTING_KEY,
                        new ReservationResponseEvent(event.getOrderId(), event.getOutcome()),
                        correlationData
                );
                correlationDataMap.put(event.getOrderId(), correlationData);
                event.setLastAttempt(Instant.now());
            }
        }
    }

    @Transactional
    public void relayWaitingEvents() {
        List<ReservationResponseEventEntity> waitingEvents = reservationResponseEventRepository.findByStatus(ReservationResponseEventEntity.OutboxEventStatus.WAITING);

        for (var event : waitingEvents) {
            CorrelationData correlationData = new CorrelationData(event.getOrderId().toString());

            rabbitTemplate.convertAndSend(
                    "",
                    RabbitMqConfig.RESERVATION_OUTCOME_ROUTING_KEY,
                    new ReservationResponseEvent(event.getOrderId(), event.getOutcome()),
                    correlationData
            );
            event.setStatus(ReservationResponseEventEntity.OutboxEventStatus.ATTEMPTED);
            event.setLastAttempt(Instant.now());
            correlationDataMap.put(event.getOrderId(), correlationData);
        }
    }

}
