ALTER TABLE demo_order ADD COLUMN stock_units INT NOT NULL DEFAULT 1, ADD COLUMN request_id VARCHAR(36), ADD UNIQUE KEY uk_order_request (request_id);
UPDATE demo_order SET stock_units=quantity WHERE request_id IS NULL;
CREATE TABLE demo_sale_lock (id INT PRIMARY KEY);
INSERT INTO demo_sale_lock VALUES (1);
CREATE TABLE demo_order_event (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL, amount DECIMAL(10,2) NOT NULL, reason VARCHAR(255) NOT NULL, operator_name VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL);
