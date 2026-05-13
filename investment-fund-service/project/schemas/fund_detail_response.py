"""Response schema for a fund detail view including computed fields."""

from datetime import date
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel


class FundDetailResponse(BaseModel):
    """Fund detail with runtime-computed vrednost_fonda and profit."""

    id: int
    naziv: str
    opis: Optional[str]
    minimalni_ulog: Decimal
    menadzer_id: int
    likvidna_sredstva: Decimal
    account_id: int
    datum_kreiranja: date
    vrednost_fonda: Decimal
    profit: Decimal
