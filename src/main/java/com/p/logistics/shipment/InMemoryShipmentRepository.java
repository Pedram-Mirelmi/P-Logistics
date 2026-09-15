package com.p.logistics.shipment;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryShipmentRepository implements ShipmentRepository {

    private final ConcurrentHashMap<UUID, Shipment> shipments =
            new ConcurrentHashMap<>();

    @Override
    public void save(Shipment shipment) {
        shipments.put(shipment.getId(), shipment);
    }

    @Override
    public Optional<Shipment> findById(UUID id) {
        return Optional.ofNullable(shipments.get(id));
    }

    @Override
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return shipments.values()
                .stream()
                .filter(shipment -> shipment.getOrderId().equals(orderId))
                .findFirst();
    }
}