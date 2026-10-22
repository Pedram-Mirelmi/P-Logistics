package com.p.logistics.inventory;

// Distinguishes "the network/remote call itself failed" (this exception)
// from "inventory answered and said no" (ReserveInventoryResponse.reserved()
// == false). Chapter 5's core question - "can the client know whether the
// operation executed?" - only applies to this case, never to the latter.
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String productId, Throwable cause) {
        super("Inventory service call failed for product " + productId, cause);
    }
}
