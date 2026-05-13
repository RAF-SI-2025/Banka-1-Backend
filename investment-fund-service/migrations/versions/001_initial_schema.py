"""Initial schema: investment_funds, client_fund_transactions, client_fund_positions, fund_performance_snapshots.

Revision ID: 001
Revises:
Create Date: 2025-01-01 00:00:00.000000
"""
from typing import Sequence, Union

from alembic import op

revision: str = "001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create all tables and the transactionstatus enum."""
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE transactionstatus AS ENUM ('PENDING', 'COMPLETED', 'FAILED');
        EXCEPTION
            WHEN duplicate_object THEN null;
        END $$
    """)

    op.execute("""
        CREATE TABLE IF NOT EXISTS investment_funds (
            id          BIGSERIAL PRIMARY KEY,
            naziv       VARCHAR(255) NOT NULL,
            opis        TEXT,
            minimalni_ulog  NUMERIC(19,4) NOT NULL,
            menadzer_id BIGINT NOT NULL,
            likvidna_sredstva NUMERIC(19,4) NOT NULL DEFAULT 0,
            account_id  BIGINT NOT NULL,
            datum_kreiranja DATE NOT NULL DEFAULT CURRENT_DATE
        )
    """)
    op.execute("CREATE UNIQUE INDEX IF NOT EXISTS ix_investment_funds_naziv ON investment_funds (naziv)")

    op.execute("""
        CREATE TABLE IF NOT EXISTS client_fund_transactions (
            id               BIGSERIAL PRIMARY KEY,
            klijent_id       BIGINT NOT NULL,
            fund_id          BIGINT NOT NULL REFERENCES investment_funds(id),
            iznos            NUMERIC(19,4) NOT NULL,
            status           transactionstatus NOT NULL,
            timestamp        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            is_inflow        BOOLEAN NOT NULL,
            source_account_id BIGINT NOT NULL,
            idempotency_key  VARCHAR(64)
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_client_fund_transactions_klijent_id ON client_fund_transactions (klijent_id)")
    op.execute("CREATE INDEX IF NOT EXISTS ix_client_fund_transactions_fund_id ON client_fund_transactions (fund_id)")
    op.execute("CREATE UNIQUE INDEX IF NOT EXISTS ix_client_fund_transactions_idempotency_key ON client_fund_transactions (idempotency_key)")

    op.execute("""
        CREATE TABLE IF NOT EXISTS client_fund_positions (
            id                    BIGSERIAL PRIMARY KEY,
            klijent_id            BIGINT NOT NULL,
            fund_id               BIGINT NOT NULL REFERENCES investment_funds(id),
            ukupan_ulozeni_iznos  NUMERIC(19,4) NOT NULL DEFAULT 0,
            datum_poslednje_promene TIMESTAMPTZ NOT NULL,
            CONSTRAINT uq_client_fund UNIQUE (klijent_id, fund_id)
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_client_fund_positions_klijent_id ON client_fund_positions (klijent_id)")
    op.execute("CREATE INDEX IF NOT EXISTS ix_client_fund_positions_fund_id ON client_fund_positions (fund_id)")

    op.execute("""
        CREATE TABLE IF NOT EXISTS fund_performance_snapshots (
            id               BIGSERIAL PRIMARY KEY,
            fund_id          BIGINT NOT NULL REFERENCES investment_funds(id),
            date             DATE NOT NULL,
            vrednost_fonda   NUMERIC(19,4) NOT NULL,
            profit           NUMERIC(19,4) NOT NULL,
            likvidna_sredstva NUMERIC(19,4) NOT NULL,
            CONSTRAINT uq_fund_date UNIQUE (fund_id, date)
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_fund_performance_snapshots_fund_id ON fund_performance_snapshots (fund_id)")


def downgrade() -> None:
    """Drop all tables and the transactionstatus enum."""
    op.execute("DROP TABLE IF EXISTS fund_performance_snapshots")
    op.execute("DROP TABLE IF EXISTS client_fund_positions")
    op.execute("DROP TABLE IF EXISTS client_fund_transactions")
    op.execute("DROP TABLE IF EXISTS investment_funds")
    op.execute("DROP TYPE IF EXISTS transactionstatus")
