-- Step 3 schema (Database Gateways), carried forward unchanged so this
-- migration is a no-op baseline on a database that already has these tables.
CREATE TABLE IF NOT EXISTS inventory_item (
    product_id VARCHAR(100) PRIMARY KEY,
    quantity INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id VARCHAR(100) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL
);
