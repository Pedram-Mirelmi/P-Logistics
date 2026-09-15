package com.p.logistics.shipment;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment createShipment(UUID orderId, String destination) {

        Shipment shipment = new Shipment(
                UUID.randomUUID(),
                orderId,
                destination,
                ShipmentStatus.CREATED
        );

        shipmentRepository.save(shipment);

        return shipment;
    }

    public Shipment getShipment(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Shipment not found"));
    }

    public Shipment getShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Shipment not found"));
    }

    public Shipment updateStatus(UUID id, ShipmentStatus status) {
        Shipment shipment = getShipment(id);

        shipment.setStatus(status);

        shipmentRepository.save(shipment);

        return shipment;
    }
}