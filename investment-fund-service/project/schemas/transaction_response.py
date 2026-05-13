"""Response schema for a client fund transaction."""

from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel

from project.enums.transaction_status import TransactionStatus


class TransactionResponse(BaseModel):
    """Read-only view of a ClientFundTransaction."""

    id: int
    klijent_id: int
    fund_id: int
    iznos: Decimal
    status: TransactionStatus
    is_inflow: bool
    timestamp: datetime
