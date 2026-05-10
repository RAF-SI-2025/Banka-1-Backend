"""Request schema for partially updating an investment fund."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field


class UpdateFundRequest(BaseModel):
    """Validated payload for PATCH /funds/{id}; all fields are optional."""

    opis: Optional[str] = Field(None, description="Updated fund description")
    minimalni_ulog: Optional[Decimal] = Field(None, gt=Decimal("0"), description="Updated minimum investment")
    menadzer_id: Optional[int] = Field(None, gt=0, description="Updated manager employee ID (must be SUPERVISOR)")
