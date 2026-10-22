-- Step 07 (Chapter 7: Message-Oriented Middleware): the transactional
-- outbox. A row here is written in the SAME local transaction as the
-- business change it describes (see OrderService.submitOrder), so
-- "the order was accepted" and "there is an event recording that" are
-- atomic together via ordinary Postgres ACID - no distributed transaction
-- needed. OutboxRelay reads PENDING rows afterward and publishes them to
-- RabbitMQ, then flips them to PUBLISHED.
CREATE TABLE IF NOT EXISTS outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type   TEXT NOT NULL,
    payload      TEXT NOT NULL,
    status       TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- OutboxRelay's poll ("find PENDING rows, oldest first") is the only query
-- this table serves.
CREATE INDEX IF NOT EXISTS idx_outbox_event_status_created_at
    ON outbox_event (status, created_at);
