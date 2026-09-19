package com.p.logistics.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    private String productId;

    private int quantity;

    @Version
    private long version;

    public InventoryItem() {
    }

    public InventoryItem(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public boolean reserve(int quantity) {
        if (quantity <= 0 || quantity > this.quantity) {
            return false;
        }

        this.quantity -= quantity;
        return true;
    }
}
