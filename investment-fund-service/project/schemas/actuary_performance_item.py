"""Schema for a single row in the actuary performances portal page."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel


class ActuaryPerformanceItem(BaseModel):
    """Actuary with their aggregated fund profit contribution."""

    id: int
    ime: Optional[str] = None
    prezime: Optional[str] = None
    role: Optional[str] = None
    profit: Decimal
