-- V20: Store Settings, Notifications, and Audit Log
CREATE TABLE IF NOT EXISTS demo_store_settings (
    id INT PRIMARY KEY,
    store_name VARCHAR(100) NOT NULL,
    business_hours VARCHAR(50) NOT NULL,
    open_time VARCHAR(10) NOT NULL,
    close_time VARCHAR(10) NOT NULL,
    buffer_minutes INT NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS demo_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_role VARCHAR(20) NOT NULL,
    customer_id BIGINT,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500) NOT NULL,
    related_no VARCHAR(64),
    unread TINYINT NOT NULL DEFAULT 1,
    urgent TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS demo_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO demo_store_settings (id, store_name, business_hours, open_time, close_time, buffer_minutes, address, phone, updated_at)
SELECT 1, 'PAW & PURE 爪印轻奢宠护中心（旗舰店）', '09:00 - 21:00', '09:00', '21:00', 15, '上海市徐汇区武康路 288 号 Apple Park 创意街区 1F', '400-820-8899', NOW()
WHERE NOT EXISTS (SELECT 1 FROM demo_store_settings WHERE id = 1);
