-- liquibase formatted sql

-- changeset order:12
-- validCheckSum: ANY
-- Idempotentno (IF NOT EXISTS) da delimicno-primenjen changeset (npr. kolona
-- created_at vec dodata u ranijem padu) ne srusi startup na postojecoj bazi.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS executed_at TIMESTAMP;
UPDATE orders SET created_at = last_modification WHERE created_at IS NULL;
UPDATE orders SET executed_at = last_modification WHERE executed_at IS NULL AND status = 'DONE';
ALTER TABLE orders ALTER COLUMN created_at SET NOT NULL;
