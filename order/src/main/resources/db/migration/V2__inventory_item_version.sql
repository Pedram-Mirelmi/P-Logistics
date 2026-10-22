-- Adds the optimistic-locking column backing InventoryItem's @Version field.
-- A separate migration (not folded into V1) so it still applies to a database
-- that already had the Step 3 inventory_item table before Flyway was introduced.
ALTER TABLE inventory_item
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
