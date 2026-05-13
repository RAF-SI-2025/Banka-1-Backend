"""Router class exposing the fund performance history endpoint."""

from typing import Annotated

from fastapi import APIRouter, Depends, Query

from project.dependencies import get_fund_performance_service, require_authenticated
from project.enums.performance_period import PerformancePeriod
from project.middleware.token_data import TokenData
from project.schemas.performance_response import PerformanceResponse
from project.schemas.performance_snapshot_item import PerformanceSnapshotItem
from project.services.fund_performance_service import FundPerformanceService


class PerformanceRouter:
    """Registers GET /funds/{fund_id}/performance and exposes it via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register the performance route handler."""
        self._router = APIRouter(prefix="/funds", tags=["performance"])
        self._router.get("/{fund_id}/performance", response_model=PerformanceResponse)(self.get_performance)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def get_performance(self, fund_id: int, service: Annotated[FundPerformanceService, Depends(get_fund_performance_service)], _token: Annotated[TokenData, Depends(require_authenticated)], period: PerformancePeriod = Query(PerformancePeriod.MONTH, description="Time window for performance history")) -> PerformanceResponse:
        """Return historical daily snapshots for the fund over the requested period."""
        snapshots = await service.get_performance(fund_id, period)
        items = [PerformanceSnapshotItem(date=s.date, vrednost_fonda=s.vrednost_fonda, profit=s.profit, likvidna_sredstva=s.likvidna_sredstva) for s in snapshots]
        return PerformanceResponse(fund_id=fund_id, period=period, snapshots=items)
