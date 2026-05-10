"""Schema for a single fund performance data point."""

from datetime import date
from decimal import Decimal

from pydantic import BaseModel


class PerformanceSnapshotItem(BaseModel):
    """One day's performance record for a fund."""

    date: date
    vrednost_fonda: Decimal
    profit: Decimal
    likvidna_sredstva: Decimal
