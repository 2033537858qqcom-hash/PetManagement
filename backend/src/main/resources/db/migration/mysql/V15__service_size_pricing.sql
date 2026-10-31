CREATE TABLE demo_service_price (service_id BIGINT NOT NULL, size_type VARCHAR(15) NOT NULL, price DECIMAL(10,2) NOT NULL, duration INT NOT NULL, PRIMARY KEY(service_id,size_type));
ALTER TABLE demo_grooming ADD COLUMN base_price DECIMAL(10,2);
