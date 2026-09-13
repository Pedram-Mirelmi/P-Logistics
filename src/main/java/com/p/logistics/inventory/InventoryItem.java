package com.p.logistics.inventory;

import lombok.Getter;

public class InventoryItem {

    @Getter
    private final String productId;

    @Getter
    private int availableQuantity;

    public InventoryItem(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    public boolean reserve(int quantity) {
        if (quantity <= 0 || quantity > availableQuantity) {
            return false;
        }

        availableQuantity -= quantity;
        return true;
    }
}
