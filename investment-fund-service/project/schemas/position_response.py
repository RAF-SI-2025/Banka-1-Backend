"""Response schema for a client's position in a fund."""

from datetime import datetime
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel


class PositionResponse(BaseModel):
    """Client fund position including computed ownership percentage, value, and profit."""

    id: int
    klijent_id: int
    fund_id: int
    fund_naziv: Optional[str] = None
    fund_opis: Optional[str] = None
    ukupan_ulozeni_iznos: Decimal
    datum_poslednje_promene: datetime
    procenat_fonda: Decimal
    trenutna_vrednost_pozicije: Decimal
    ostvareni_profit: Decimal
    vrednost_fonda: Optional[Decimal] = None
