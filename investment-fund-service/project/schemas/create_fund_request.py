"""Request schema for creating a new investment fund."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field


class CreateFundRequest(BaseModel):
    """Validated payload for POST /funds."""

    naziv: str = Field(..., min_length=1, max_length=255, description="Unique fund name")
    opis: Optional[str] = Field(None, description="Fund description")
    minimalni_ulog: Decimal = Field(..., gt=Decimal("0"), description="Minimum investment amount in RSD")
    menadzer_id: int = Field(..., gt=0, description="Employee ID of the fund manager (must be SUPERVISOR)")
