ALTER TABLE demo_addon ADD COLUMN request_id VARCHAR(36), ADD UNIQUE KEY uk_addon_request (request_id);
