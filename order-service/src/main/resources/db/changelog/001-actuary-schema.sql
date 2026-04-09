-- liquibase formatted sql

-- changeset order:1
CREATE TABLE actuary_info (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT         NOT NULL UNIQUE,
    "limit"     DECIMAL(19, 4),
    used_limit  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    need_approval BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_actuary_info_employee_id ON actuary_info (employee_id);
