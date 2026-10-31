ALTER TABLE demo_card MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE demo_balance_flow MODIFY grooming_id BIGINT NULL;
CREATE TABLE demo_member_lock (id INT PRIMARY KEY);
INSERT INTO demo_member_lock VALUES (1);
CREATE TABLE demo_member_recharge (request_id VARCHAR(36) PRIMARY KEY, customer_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL, operator_name VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE demo_card_sale (request_id VARCHAR(36) PRIMARY KEY, card_id BIGINT NOT NULL UNIQUE, customer_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL, operator_name VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL);
