package com.p.logistics.shipment;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class Shipment {

    private UUID id;
    private UUID orderId;
    private String destination;
    private ShipmentStatus status;

    public Shipment(UUID id, UUID orderId, String destination, ShipmentStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.destination = destination;
        this.status = status;
    }
}