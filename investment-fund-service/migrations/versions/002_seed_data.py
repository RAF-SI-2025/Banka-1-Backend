"""Seed data: sample investment funds for development.

Revision ID: 002
Revises: 001
Create Date: 2025-01-01 00:00:01.000000
"""
from typing import Sequence, Union

from alembic import op

revision: str = "002"
down_revision: Union[str, None] = "001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Insert sample investment funds."""
    op.execute("""
        INSERT INTO investment_funds (naziv, opis, minimalni_ulog, menadzer_id, likvidna_sredstva, account_id, datum_kreiranja)
        VALUES
        (
            'Banka1 Konzervativni Fond',
            'Nisko-rizični fond koji investira u državne obveznice i stabilne instrumente tržišta novca.',
            1000.0000,
            1,
            5000000.0000,
            1,
            '2024-01-15'
        ),
        (
            'Banka1 Uravnoteženi Fond',
            'Mešoviti fond koji kombinuje akcije i obveznice za umeren rast uz kontrolisan rizik.',
            5000.0000,
            1,
            12000000.0000,
            2,
            '2024-01-15'
        ),
        (
            'Banka1 Rastući Fond',
            'Agresivni akcijski fond fokusiran na kompanije sa visokim potencijalom rasta.',
            10000.0000,
            2,
            8500000.0000,
            3,
            '2024-03-01'
        ),
        (
            'Banka1 Tehnološki Fond',
            'Specijalizovani fond koji investira u tehnološki sektor i digitalne inovacije.',
            20000.0000,
            2,
            3200000.0000,
            4,
            '2024-06-01'
        )
    """)


def downgrade() -> None:
    """Remove seed investment funds."""
    op.execute("""
        DELETE FROM investment_funds
        WHERE naziv IN (
            'Banka1 Konzervativni Fond',
            'Banka1 Uravnoteženi Fond',
            'Banka1 Rastući Fond',
            'Banka1 Tehnološki Fond'
        )
    """)
