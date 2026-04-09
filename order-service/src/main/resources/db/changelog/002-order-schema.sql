-- liquibase formatted sql

-- changeset order:2
CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    listing_id          BIGINT          NOT NULL,
    order_type          VARCHAR(20)     NOT NULL,
    quantity            INTEGER         NOT NULL,
    contract_size       INTEGER         NOT NULL,
    price_per_unit      DECIMAL(19, 4)  NOT NULL,
    limit_value         DECIMAL(19, 4),
    stop_value          DECIMAL(19, 4),
    direction           VARCHAR(10)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    approved_by         BIGINT,
    is_done             BOOLEAN         NOT NULL DEFAULT FALSE,
    last_modification   TIMESTAMP       NOT NULL,
    remaining_portions  INTEGER         NOT NULL,
    after_hours         BOOLEAN         NOT NULL DEFAULT FALSE,
    all_or_none         BOOLEAN         NOT NULL DEFAULT FALSE,
    margin              BOOLEAN         NOT NULL DEFAULT FALSE,
    account_id          BIGINT          NOT NULL
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_listing_id ON orders (listing_id);
