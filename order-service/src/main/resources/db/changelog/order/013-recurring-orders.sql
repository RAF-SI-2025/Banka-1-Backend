-- liquibase formatted sql

-- changeset order:13
-- validCheckSum: ANY
-- validCheckSum ANY: changeset je u proslosti menjan posle primene (checksum
-- mutacija) -> na postojecoj bazi bi se srusio startup. ANY toleriše promenu
-- checksum-a bez ponovnog izvrsavanja.
CREATE TABLE IF NOT EXISTS recurring_orders (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    listing_id          BIGINT          NOT NULL,
    direction           VARCHAR(10)     NOT NULL,
    mode                VARCHAR(20)     NOT NULL,
    -- "value" is quoted: reserved in H2, non-reserved keyword in PostgreSQL.
    "value"             NUMERIC(19, 4)  NOT NULL,
    account_id          BIGINT          NOT NULL,
    cadence             VARCHAR(20)     NOT NULL,
    next_run            TIMESTAMP       NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recurring_orders_user_id ON recurring_orders (user_id);
CREATE INDEX IF NOT EXISTS idx_recurring_orders_due ON recurring_orders (active, next_run);
