"""Service for recording and retrieving historical fund performance snapshots."""

import logging
from datetime import date, timedelta
from typing import List

from project.enums.performance_period import PerformancePeriod
from project.models.fund_performance_snapshot import FundPerformanceSnapshot
from project.repositories.fund_performance_repository import FundPerformanceRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.services.fund_valuation_service import FundValuationService

logger = logging.getLogger(__name__)

_PERIOD_DAYS = {PerformancePeriod.MONTH: 30, PerformancePeriod.QUARTER: 90, PerformancePeriod.YEAR: 365}


class FundPerformanceService:
    """Captures daily snapshots via APScheduler and serves historical performance data."""

    def __init__(self, fund_repo: InvestmentFundRepository, perf_repo: FundPerformanceRepository, valuation_service: FundValuationService) -> None:
        """Initialise with fund and performance repositories and the valuation service."""
        self._fund_repo = fund_repo
        self._perf_repo = perf_repo
        self._valuation = valuation_service

    async def take_daily_snapshot(self, bearer_token: str = "") -> None:
        """Compute and persist a daily performance snapshot for every active fund."""
        funds = await self._fund_repo.find_all()
        for fund in funds:
            try:
                vrednost_fonda = await self._valuation.compute_vrednost_fonda(fund, bearer_token)
                profit = await self._valuation.compute_profit(fund, vrednost_fonda)
                await self._perf_repo.upsert_snapshot(fund.id, date.today(), vrednost_fonda, profit, fund.likvidna_sredstva)
            except Exception as exc:
                logger.error("Snapshot failed for fund %s: %s", fund.id, exc)
                continue

    async def get_performance(self, fund_id: int, period: PerformancePeriod) -> List[FundPerformanceSnapshot]:
        """Return historical snapshots for the given fund and period."""
        since = date.today() - timedelta(days=_PERIOD_DAYS[period])
        return await self._perf_repo.find_by_fund_and_period(fund_id, since)

    @staticmethod
    def _period_to_days(period: PerformancePeriod) -> int:
        """Map a PerformancePeriod enum value to the corresponding number of days."""
        return _PERIOD_DAYS[period]
