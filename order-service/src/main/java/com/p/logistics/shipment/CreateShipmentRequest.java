package com.p.logistics.shipment;

import java.util.UUID;

public record CreateShipmentRequest(
        UUID orderId,
        String destination
) {}