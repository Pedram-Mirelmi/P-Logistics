-- Written on every consumed OrderRegistered event, so ids come from a pooled
-- sequence: INCREMENT BY must equal the entity's allocationSize (50).
CREATE SEQUENCE IF NOT EXISTS reservation_requests_seq INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS reservation_requests (
    id         BIGINT PRIMARY KEY,
    order_id   VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    quantity   INTEGER NOT NULL,
    status     VARCHAR(15) NOT NULL,
    UNIQUE (order_id, product_id)
);
