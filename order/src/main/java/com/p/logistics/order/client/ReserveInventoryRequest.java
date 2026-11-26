package com.p.logistics.inventory;

// The client-side half of the wire contract with inventory-service. Kept as
// order-service's own copy rather than a shared jar: the two services are
// independently deployable and, per the IDL idea from the chapter, agree on
// a contract without sharing code.
public record ReserveInventoryRequest(int quantity) {
}
