-- Step 06 (Chapter 6: Transactional Middleware): scratch table for the
-- transaction-propagation exercise (PropagationLabService /
-- PropagationLabInnerService). Not part of the order/shipment domain -
-- exists only so the propagation experiments have something real to write
-- to and roll back.
CREATE TABLE IF NOT EXISTS propagation_lab (
    id   TEXT PRIMARY KEY,
    note TEXT NOT NULL
);
