"""Request schema for investing (depositing) into a fund."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field


class InvestRequest(BaseModel):
    """Validated payload for POST /funds/{id}/invest."""

    iznos: Decimal = Field(..., gt=Decimal("0"), description="Amount to invest in RSD")
    source_account_id: int = Field(..., gt=0, description="Client account ID to debit")
    idempotency_key: Optional[str] = Field(None, max_length=64, description="Client-supplied idempotency key; generated if omitted")
