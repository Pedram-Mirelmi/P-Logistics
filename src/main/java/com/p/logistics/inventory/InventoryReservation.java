package com.p.logistics.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

// A separate aggregate from InventoryItem (same style as Shipment/Order):
// it records the raw productId, not a @ManyToOne, so reserving stock never
// has to load or lock the InventoryItem graph just to leave an audit trail.
@Getter
@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    private String id;

    private String productId;

    private int quantity;

    public InventoryReservation() {
    }

    public InventoryReservation(String id, String productId, int quantity) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
    }
}
