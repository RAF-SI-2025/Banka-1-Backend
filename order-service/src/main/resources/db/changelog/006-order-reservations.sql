-- liquibase formatted sql

-- changeset order:6
ALTER TABLE actuary_info
    ADD COLUMN reserved_limit DECIMAL(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD COLUMN exchange_closed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    ADD COLUMN reserved_limit_exposure DECIMAL(19, 4) NOT NULL DEFAULT 0;
