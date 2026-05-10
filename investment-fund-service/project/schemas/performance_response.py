"""Response schema for the fund performance history endpoint."""

from typing import List

from pydantic import BaseModel

from project.enums.performance_period import PerformancePeriod
from project.schemas.performance_snapshot_item import PerformanceSnapshotItem


class PerformanceResponse(BaseModel):
    """Aggregated performance history for a fund over a given period."""

    fund_id: int
    period: PerformancePeriod
    snapshots: List[PerformanceSnapshotItem]
