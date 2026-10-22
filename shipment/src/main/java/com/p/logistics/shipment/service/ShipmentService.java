package com.p.logistics.shipment.service;

import com.p.logistics.shipment.entity.Shipment;
import com.p.logistics.shipment.repository.JpaShipmentRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ShipmentService {

    private final JpaShipmentRepository shipmentRepository;

    public ShipmentService(JpaShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment createShipment(UUID orderId, String destination) {

        Shipment shipment = new Shipment(
                UUID.randomUUID(),
                orderId,
                destination,
                Shipment.ShipmentStatus.CREATED
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
        return findShipmentByOrderId(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Shipment not found"));
    }

    // Non-throwing lookup - OrderAcceptedListener's idempotency check needs
    // to ask "does one already exist?" without an exception standing in for
    // a perfectly normal "not yet" answer.
    public Optional<Shipment> findShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }

    public Shipment updateStatus(UUID id, Shipment.ShipmentStatus status) {
        Shipment shipment = getShipment(id);

        shipment.setStatus(status);

        shipmentRepository.save(shipment);

        return shipment;
    }
}