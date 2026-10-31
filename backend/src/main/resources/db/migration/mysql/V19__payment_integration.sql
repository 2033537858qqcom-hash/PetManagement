CREATE TABLE demo_payment_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL UNIQUE,
    grooming_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    trade_no VARCHAR(64),
    qr_code VARCHAR(500),
    expire_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_grooming_id (grooming_id),
    INDEX idx_customer_id (customer_id)
);

ALTER TABLE demo_service_settlement ADD COLUMN trade_no VARCHAR(64);
