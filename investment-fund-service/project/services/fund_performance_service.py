"""Service for recording and retrieving historical fund performance snapshots."""

from datetime import date, timedelta
from typing import List

from project.enums.performance_period import PerformancePeriod
from project.models.fund_performance_snapshot import FundPerformanceSnapshot
from project.repositories.fund_performance_repository import FundPerformanceRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.services.fund_valuation_service import FundValuationService


class FundPerformanceService:
    """Captures daily snapshots via APScheduler and serves historical performance data."""

    def __init__(self, fund_repo: InvestmentFundRepository, perf_repo: FundPerformanceRepository, valuation_service: FundValuationService) -> None:
        """Initialise with fund and performance repositories and the valuation service."""
        self._fund_repo = fund_repo
        self._perf_repo = perf_repo
        self._valuation = valuation_service

    async def take_daily_snapshot(self) -> None:
        """Compute and persist a daily performance snapshot for every active fund."""
        funds = await self._fund_repo.find_all()
        for fund in funds:
            try:
                vrednost_fonda = await self._valuation.compute_vrednost_fonda(fund, "")
                profit = await self._valuation.compute_profit(fund, vrednost_fonda)
                snapshot = FundPerformanceSnapshot(fund_id=fund.id, date=date.today(), vrednost_fonda=vrednost_fonda, profit=profit, likvidna_sredstva=fund.likvidna_sredstva)
                await self._perf_repo.save(snapshot)
            except Exception:
                continue

    async def get_performance(self, fund_id: int, period: PerformancePeriod) -> List[FundPerformanceSnapshot]:
        """Return historical snapshots for the given fund and period."""
        today = date.today()
        period_days = {PerformancePeriod.MONTH: 30, PerformancePeriod.QUARTER: 90, PerformancePeriod.YEAR: 365}
        since = today - timedelta(days=period_days[period])
        return await self._perf_repo.find_by_fund_and_period(fund_id, since)

    @staticmethod
    def _period_to_days(period: PerformancePeriod) -> int:
        """Map a PerformancePeriod enum value to the corresponding number of days."""
        return {PerformancePeriod.MONTH: 30, PerformancePeriod.QUARTER: 90, PerformancePeriod.YEAR: 365}[period]
