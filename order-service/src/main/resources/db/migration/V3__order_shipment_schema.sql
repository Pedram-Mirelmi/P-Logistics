-- Object-persistence model for Step 4 (Chapter 4: Object Persistence Services):
-- the Order aggregate (orders + order_line) and the Shipment aggregate.
-- Order/Shipment ids are application-assigned UUIDs (see OrderService,
-- ShipmentService), so there is no DB-generated default on those columns.
CREATE TABLE IF NOT EXISTS orders (
    id     UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS order_line (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   UUID NOT NULL REFERENCES orders (id),
    product_id VARCHAR(100) NOT NULL,
    quantity   INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_line_order_id ON order_line (order_id);

-- Shipment references orders.id at the database level for referential
-- integrity, but the Shipment entity itself only stores the raw UUID
-- (no @ManyToOne): Order and Shipment are separate aggregates.
CREATE TABLE IF NOT EXISTS shipment (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders (id),
    destination VARCHAR(255) NOT NULL,
    status      VARCHAR(20) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shipment_order_id ON shipment (order_id);
