"""Enum representing the time period for fund performance queries."""

from enum import Enum


class PerformancePeriod(str, Enum):
    """Supported time windows for historical fund performance."""

    MONTH = "MONTH"
    QUARTER = "QUARTER"
    YEAR = "YEAR"
