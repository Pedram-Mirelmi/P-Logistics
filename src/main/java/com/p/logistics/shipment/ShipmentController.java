package com.p.logistics.shipment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ResponseEntity<Shipment> createShipment(
            @RequestBody CreateShipmentRequest request
    ) {
        return ResponseEntity.ok(
                shipmentService.createShipment(
                        request.orderId(),
                        request.destination()
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Shipment> getShipment(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shipmentService.getShipment(id)
        );
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Shipment> updateStatus(
            @PathVariable UUID id,
            @RequestParam ShipmentStatus status
    ) {
        return ResponseEntity.ok(
                shipmentService.updateStatus(id, status)
        );
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Shipment> getShipmentByOrderId(
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(
                shipmentService.getShipmentByOrderId(orderId)
        );
    }
}