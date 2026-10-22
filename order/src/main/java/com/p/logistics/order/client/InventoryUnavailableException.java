package com.p.logistics.order.client;

// Distinguishes "the network/remote call itself failed" (this exception)
// from "inventory answered" (a ReservationOutcome, including "no").
// Chapter 5's core question - "can the client know whether the operation
// executed?" - only applies to this case, never to the latter.
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message) {
        super(message);
    }

    public InventoryUnavailableException(Throwable cause) {
        super("Inventory service batch-reserve call failed", cause);
    }
}
