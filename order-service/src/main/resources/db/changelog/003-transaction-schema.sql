-- liquibase formatted sql

-- changeset order:3
CREATE TABLE transactions (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id),
    quantity        INTEGER         NOT NULL,
    price_per_unit  DECIMAL(19, 4)  NOT NULL,
    total_price     DECIMAL(19, 4)  NOT NULL,
    commission      DECIMAL(19, 4)  NOT NULL,
    timestamp       TIMESTAMP       NOT NULL
);

CREATE INDEX idx_transactions_order_id ON transactions (order_id);
