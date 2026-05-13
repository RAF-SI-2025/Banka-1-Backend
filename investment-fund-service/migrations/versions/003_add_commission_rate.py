"""Add commission_rate column to client_fund_transactions.

Revision ID: 003
Revises: 002
Create Date: 2026-05-13 00:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "003"
down_revision: Union[str, None] = "002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add commission_rate column with default 0 to client_fund_transactions."""
    op.execute("""
        ALTER TABLE client_fund_transactions
        ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(10, 6) NOT NULL DEFAULT 0
    """)


def downgrade() -> None:
    """Remove commission_rate column from client_fund_transactions."""
    op.execute("ALTER TABLE client_fund_transactions DROP COLUMN IF EXISTS commission_rate")
