"""Response schema returned after a fund redemption attempt."""

from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel

from project.enums.transaction_status import TransactionStatus


class RedeemResponse(BaseModel):
    """Result of a POST /funds/{id}/redeem call."""

    transaction_id: int
    status: TransactionStatus
    iznos: Decimal
    fund_id: int
    klijent_id: int
    liquidation_started: bool
    timestamp: datetime
