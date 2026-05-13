"""Response schema for a fund summary (list/create/update)."""

from datetime import date
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, ConfigDict


class FundResponse(BaseModel):
    """Serialised representation of an InvestmentFund; computed fields populated by the router."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    naziv: str
    opis: Optional[str]
    minimalni_ulog: Decimal
    menadzer_id: int
    likvidna_sredstva: Decimal
    account_id: int
    datum_kreiranja: date
    vrednost_fonda: Optional[Decimal] = None
    profit: Optional[Decimal] = None
