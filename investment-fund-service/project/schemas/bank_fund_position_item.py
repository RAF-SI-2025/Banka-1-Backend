"""Response schema for a single fund entry in the bank profit portal."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel


class BankFundPositionItem(BaseModel):
    """Bank's ownership snapshot for one investment fund."""

    fund_id: int
    naziv: str
    menadzer_id: int
    menadzer_ime: Optional[str] = None
    menadzer_prezime: Optional[str] = None
    bank_procenat: Decimal
    bank_vrednost: Decimal
    bank_profit: Decimal
