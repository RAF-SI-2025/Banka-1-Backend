"""Request schema for redeeming (withdrawing) from a fund."""

from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field, model_validator


class RedeemRequest(BaseModel):
    """Validated payload for POST /funds/{id}/redeem."""

    iznos: Optional[Decimal] = Field(None, gt=Decimal("0"), description="Amount to withdraw in RSD; omit when withdraw_all=true")
    destination_account_id: int = Field(..., gt=0, description="Client account ID to credit")
    withdraw_all: bool = Field(False, description="When true, withdraw the entire position; iznos is ignored")
    idempotency_key: Optional[str] = Field(None, max_length=64, description="Optional idempotency key to prevent duplicate withdrawals")

    @model_validator(mode="after")
    def require_iznos_or_withdraw_all(self) -> "RedeemRequest":
        """Ensure iznos is provided when withdraw_all is false."""
        if not self.withdraw_all and self.iznos is None:
            raise ValueError("iznos must be provided when withdraw_all is false")
        return self
