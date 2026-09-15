package com.p.logistics.shipment;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    void save(Shipment shipment);

    Optional<Shipment> findById(UUID id);

    Optional<Shipment> findByOrderId(UUID orderId);
}