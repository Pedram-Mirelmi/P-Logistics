-- Step 05 (Chapter 5: RPC & Distributed Transactions): Inventory becomes its
-- own deployed service with its own database (see inventory-service's own
-- migration history). These tables no longer belong to the order-service
-- database - the two are now independent resource managers, reachable from
-- each other only through the network, never through a shared local
-- transaction.
DROP TABLE IF EXISTS inventory_reservation;
DROP TABLE IF EXISTS inventory_item;
