package com.p.logistics.inventory.messaging.event;

import com.p.logistics.inventory.reservation.entity.ReservationOutcome;

import java.util.UUID;

public record ReservationResponseEvent(UUID orderId, ReservationOutcome outcome) {
}
