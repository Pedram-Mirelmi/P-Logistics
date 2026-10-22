package com.p.logistics.shipment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "shipment")
@NoArgsConstructor
public class Shipment {

    @Id
    private UUID id;

    private UUID orderId;

    private String destination;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    public Shipment(UUID id, UUID orderId, String destination, ShipmentStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.destination = destination;
        this.status = status;
    }
}