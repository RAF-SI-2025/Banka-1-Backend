"""Enum representing the lifecycle status of a fund transaction."""

from enum import Enum


class TransactionStatus(str, Enum):
    """Possible states for a ClientFundTransaction."""

    PENDING = "PENDING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
