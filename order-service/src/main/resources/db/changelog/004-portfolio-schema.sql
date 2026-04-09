-- liquibase formatted sql

-- changeset order:4
CREATE TABLE portfolio (
    id                      BIGSERIAL       PRIMARY KEY,
    user_id                 BIGINT          NOT NULL,
    listing_id              BIGINT          NOT NULL,
    listing_type            VARCHAR(20)     NOT NULL,
    quantity                INTEGER         NOT NULL,
    average_purchase_price  DECIMAL(19, 4)  NOT NULL,
    is_public               BOOLEAN         NOT NULL DEFAULT FALSE,
    public_quantity         INTEGER         NOT NULL DEFAULT 0,
    last_modified           TIMESTAMP       NOT NULL,
    CONSTRAINT uq_portfolio_user_listing UNIQUE (user_id, listing_id)
);

CREATE INDEX idx_portfolio_user_id ON portfolio (user_id);
