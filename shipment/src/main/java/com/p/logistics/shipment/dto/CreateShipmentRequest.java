package com.p.logistics.shipment.dto;

import java.util.UUID;

public record CreateShipmentRequest(
        UUID orderId,
        String destination
) {}