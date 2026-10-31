-- Initial schema. Applied once by Flyway.
CREATE TABLE demo_customer (id BIGINT PRIMARY KEY, name VARCHAR(60), phone VARCHAR(30), level_name VARCHAR(20), balance DECIMAL(10,2), points INT);
CREATE TABLE demo_account_binding (username VARCHAR(64) PRIMARY KEY, customer_id BIGINT, employee_id BIGINT);
CREATE TABLE demo_pet (id BIGINT PRIMARY KEY, customer_id BIGINT, name VARCHAR(60), species VARCHAR(10), breed VARCHAR(60), size_type VARCHAR(15), weight DECIMAL(5,2), coat VARCHAR(30), health_note VARCHAR(300), age_band VARCHAR(20), allergy VARCHAR(40), skin_history VARCHAR(40), grooming_history VARCHAR(40));
CREATE TABLE demo_service (id BIGINT PRIMARY KEY, name VARCHAR(80), category VARCHAR(30), price DECIMAL(10,2), duration INT, species VARCHAR(10), enabled TINYINT NOT NULL DEFAULT 1);
CREATE TABLE demo_employee (id BIGINT PRIMARY KEY, name VARCHAR(60), title VARCHAR(60), skills VARCHAR(200), species VARCHAR(30), sizes VARCHAR(80));
CREATE TABLE demo_resource (id BIGINT PRIMARY KEY, name VARCHAR(80), species VARCHAR(20));
CREATE TABLE demo_appointment (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT, pet_id BIGINT, service_id BIGINT, employee_id BIGINT, resource_id BIGINT, appointment_date DATE, start_time TIME, end_time TIME, price DECIMAL(10,2), status VARCHAR(30));
CREATE TABLE demo_grooming (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, pet_id BIGINT, customer_id BIGINT, employee_id BIGINT, service_id BIGINT, stage VARCHAR(30), status VARCHAR(30), pickup_code VARCHAR(10), appointment_id BIGINT UNIQUE, resource_id BIGINT);
CREATE TABLE demo_grooming_event (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, grooming_id BIGINT NOT NULL, event_type VARCHAR(30) NOT NULL, operator_name VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE demo_addon (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, grooming_id BIGINT, name VARCHAR(100), reason VARCHAR(300), amount DECIMAL(10,2), status VARCHAR(20));
CREATE TABLE demo_product (id BIGINT PRIMARY KEY, name VARCHAR(100), stock INT, threshold INT, unit VARCHAR(20), price DECIMAL(10,2) NOT NULL DEFAULT 0);
CREATE TABLE demo_card (id BIGINT PRIMARY KEY, customer_id BIGINT, name VARCHAR(100), remaining INT, total INT);
CREATE TABLE demo_review (id BIGINT PRIMARY KEY, customer_name VARCHAR(60), pet_name VARCHAR(60), rating INT, content VARCHAR(500));
CREATE TABLE demo_health_record (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, pet_id BIGINT NOT NULL, grooming_id BIGINT, employee_id BIGINT, recorded_on DATE NOT NULL, weight DECIMAL(5,2) NOT NULL, skin_code VARCHAR(30) NOT NULL, coat_code VARCHAR(30) NOT NULL, ear_code VARCHAR(30) NOT NULL, score INT NOT NULL);
CREATE TABLE demo_order (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL, product_id BIGINT NOT NULL, product_name VARCHAR(100) NOT NULL, quantity INT NOT NULL, unit_price DECIMAL(10,2) NOT NULL, amount DECIMAL(10,2) NOT NULL, status VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL);

