"""Request schema for redeeming (withdrawing) from a fund."""

from decimal import Decimal

from pydantic import BaseModel, Field


class RedeemRequest(BaseModel):
    """Validated payload for POST /funds/{id}/redeem."""

    iznos: Decimal = Field(..., gt=Decimal("0"), description="Amount to withdraw in RSD")
    destination_account_id: int = Field(..., gt=0, description="Client account ID to credit")
