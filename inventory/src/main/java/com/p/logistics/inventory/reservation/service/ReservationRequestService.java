package com.p.logistics.inventory.reservation.service;

import com.p.logistics.inventory.reservation.entity.ReservationOutcome;
import com.p.logistics.inventory.reservation.entity.ReservationRequest;
import com.p.logistics.inventory.inventory_item.service.InventoryService;
import com.p.logistics.inventory.messaging.event.OrderRegisteredEvent;
import com.p.logistics.inventory.messaging.service.OutBoxService;
import com.p.logistics.inventory.reservation.repository.ItemReservationRequestRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationRequestService {
    private final ItemReservationRequestRepository itemReservationRequestRepository;
    private final InventoryService inventoryService;
    private OutBoxService outBoxService;

    public ReservationRequestService(ItemReservationRequestRepository itemReservationRequestRepository, InventoryService inventoryService) {
        this.itemReservationRequestRepository = itemReservationRequestRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public void handleOrderRegisteredEvent(OrderRegisteredEvent event) {
        if (itemReservationRequestRepository.existsByOrderId((event.orderId()))) {
            outBoxService.saveReservationOutcomeToPublish(event.orderId(), ReservationOutcome.UNKNOWN_FAILURE);
            return;
        }

        List<ReservationRequest> requests =  event.orderItems()
                .stream()
                .map(
                        item -> new ReservationRequest(event.orderId(), item.productId(), item.quantity(), ReservationOutcome.UNKNOWN_FAILURE)
                )
                .toList();

        ReservationOutcome reservationOutcome = ReservationOutcome.UNKNOWN_FAILURE;
        try {
            reservationOutcome = inventoryService.batchReserve(event.orderItems());
        } catch (RuntimeException _) {
        } finally {
            for (ReservationRequest request : requests) {
                request.setStatus(reservationOutcome);
            }
            itemReservationRequestRepository.saveAll(requests);
            outBoxService.saveReservationOutcomeToPublish(event.orderId(), reservationOutcome);
        }
    }
}
