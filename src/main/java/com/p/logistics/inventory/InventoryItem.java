package com.p.logistics.inventory;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InventoryItem {

    private String productId;

    private int quantity;

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
