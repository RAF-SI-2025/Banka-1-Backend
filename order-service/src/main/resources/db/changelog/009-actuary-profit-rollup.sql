-- liquibase formatted sql

-- changeset order:9
CREATE TABLE actuary_profit_rollup (
    employee_id       BIGINT          PRIMARY KEY,
    ime               VARCHAR(255)    NOT NULL,
    prezime           VARCHAR(255)    NOT NULL,
    role              VARCHAR(16)     NOT NULL,
    total_profit_rsd  DECIMAL(19, 4)  NOT NULL DEFAULT 0,
    refreshed_at      TIMESTAMP       NOT NULL
);
