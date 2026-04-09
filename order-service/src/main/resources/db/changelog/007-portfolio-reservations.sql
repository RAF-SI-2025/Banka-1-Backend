-- liquibase formatted sql

-- changeset order:7
ALTER TABLE portfolio
    ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0;
