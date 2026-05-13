"""Response schema returned after a fund investment attempt."""

from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel

from project.enums.transaction_status import TransactionStatus


class InvestResponse(BaseModel):
    """Result of a POST /funds/{id}/invest call."""

    transaction_id: int
    status: TransactionStatus
    iznos: Decimal
    fund_id: int
    klijent_id: int
    timestamp: datetime
