"""Response schema for a fund detail view including computed fields."""

from datetime import date
from decimal import Decimal
from typing import List, Optional

from pydantic import BaseModel

from project.schemas.security_item import SecurityItem


class FundDetailResponse(BaseModel):
    """Fund detail with runtime-computed vrednost_fonda, profit, and securities list."""

    id: int
    naziv: str
    opis: Optional[str]
    minimalni_ulog: Decimal
    menadzer_id: int
    menadzer_ime: Optional[str] = None
    menadzer_prezime: Optional[str] = None
    likvidna_sredstva: Decimal
    account_id: int
    datum_kreiranja: date
    vrednost_fonda: Decimal
    profit: Decimal
    securities: List[SecurityItem] = []
