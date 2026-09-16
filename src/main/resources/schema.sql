CREATE TABLE IF NOT EXISTS inventory_item (
    product_id VARCHAR(100) PRIMARY KEY,
    quantity INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS inventory_reservation (
         id VARCHAR(100) PRIMARY KEY,
         product_id VARCHAR(100) NOT NULL,
         quantity INTEGER NOT NULL
);