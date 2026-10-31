ALTER TABLE demo_inventory_flow ADD COLUMN request_id VARCHAR(36), ADD UNIQUE KEY uk_inventory_request (request_id);
