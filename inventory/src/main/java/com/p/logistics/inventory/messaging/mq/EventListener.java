package com.p.logistics.inventory.messaging.mq;

import com.p.logistics.inventory.messaging.event.OrderRegisteredEvent;
import com.p.logistics.inventory.reservation.service.ReservationRequestService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EventListener {

    private final ReservationRequestService reservationRequestService;

    @PersistenceContext
    private final EntityManager entityManager;

    public EventListener(ReservationRequestService reservationRequestService, EntityManager entityManager) {
        this.reservationRequestService = reservationRequestService;
        this.entityManager = entityManager;
    }

    @RabbitListener(queues = RabbitMqConfig.ORDER_REGISTERED_QUEUE)
    public void onOrderedRegistered(OrderRegisteredEvent event) {
        reservationRequestService.handleOrderRegisteredEvent(event);
    }
}
