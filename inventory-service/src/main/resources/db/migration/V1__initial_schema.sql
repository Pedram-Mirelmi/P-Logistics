-- inventory-service's own database, independent of order-service's. This is
-- a brand new schema (no Step 3 legacy tables to baseline against), so it
-- starts clean at V1 with the optimistic-locking version column already in
-- place, instead of carrying the order-service history's V1/V2 split.
CREATE TABLE IF NOT EXISTS inventory_item (
    product_id VARCHAR(100) PRIMARY KEY,
    quantity   INTEGER NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id         VARCHAR(100) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL,
    quantity   INTEGER NOT NULL
);
