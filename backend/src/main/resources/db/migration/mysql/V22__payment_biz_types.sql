ALTER TABLE payment_order MODIFY grooming_id BIGINT NULL;
ALTER TABLE payment_order ADD COLUMN biz_type VARCHAR(20) NOT NULL DEFAULT 'GROOMING';
ALTER TABLE payment_order ADD COLUMN related_id BIGINT NULL;
UPDATE payment_order SET biz_type = 'GROOMING', related_id = grooming_id WHERE biz_type = 'GROOMING' AND related_id IS NULL;
