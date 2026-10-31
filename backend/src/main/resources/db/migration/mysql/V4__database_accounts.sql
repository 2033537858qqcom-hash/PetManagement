ALTER TABLE demo_account_binding ADD COLUMN password_hash VARCHAR(100), ADD COLUMN role_code VARCHAR(20), ADD COLUMN display_name VARCHAR(60), ADD COLUMN title VARCHAR(60), ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1;
INSERT INTO demo_account_binding(username,role_code,display_name,title) VALUES ('admin','ADMIN','系统管理员','系统管理');
