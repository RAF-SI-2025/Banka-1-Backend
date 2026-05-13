"""Schema for a single security held by an investment fund."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel


class SecurityItem(BaseModel):
    """One security position returned in the fund detail view."""

    ticker: Optional[str] = None
    price: Decimal
    change: Optional[Decimal] = None
    volume: Optional[int] = None
    initial_margin_cost: Optional[Decimal] = None
    acquisition_date: Optional[str] = None
    quantity: int
