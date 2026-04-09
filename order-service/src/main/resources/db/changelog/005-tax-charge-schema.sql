-- liquibase formatted sql

-- changeset order:5
CREATE TABLE tax_charges (
    id                   BIGSERIAL       PRIMARY KEY,
    sell_transaction_id  BIGINT          NOT NULL REFERENCES transactions(id),
    buy_transaction_id   BIGINT          NOT NULL REFERENCES transactions(id),
    user_id              BIGINT          NOT NULL,
    listing_id           BIGINT          NOT NULL,
    source_account_id    BIGINT          NOT NULL,
    tax_period_start     TIMESTAMP       NOT NULL,
    tax_period_end       TIMESTAMP       NOT NULL,
    tax_amount           DECIMAL(19, 4)  NOT NULL,
    tax_amount_rsd       DECIMAL(19, 4),
    status               VARCHAR(20)     NOT NULL,
    created_at           TIMESTAMP       NOT NULL,
    charged_at           TIMESTAMP
);

CREATE UNIQUE INDEX uk_tax_charges_sell_buy
    ON tax_charges (sell_transaction_id, buy_transaction_id);

CREATE INDEX idx_tax_charges_period
    ON tax_charges (tax_period_start, tax_period_end);
