"""Unit tests for FundPerformanceService."""

from datetime import date, timedelta
from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

import pytest

from project.enums.performance_period import PerformancePeriod
from project.services.fund_performance_service import FundPerformanceService


def _make_svc(fund_repo, perf_repo, valuation_service) -> FundPerformanceService:
    """Construct a FundPerformanceService with the given dependencies."""
    return FundPerformanceService(fund_repo, perf_repo, valuation_service)


class TestTakeDailySnapshot:
    """Tests for FundPerformanceService.take_daily_snapshot."""

    async def test_snapshot_saved_for_each_fund(self, mock_fund_repo, mock_perf_repo, fund):
        """take_daily_snapshot persists one upsert per fund returned by the repository."""
        valuation = MagicMock()
        valuation.compute_vrednost_fonda = AsyncMock(return_value=Decimal("60000.00"))
        valuation.compute_profit = AsyncMock(return_value=Decimal("10000.00"))
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        await svc.take_daily_snapshot()
        mock_perf_repo.upsert_snapshot.assert_awaited_once()

    async def test_snapshot_contains_correct_values(self, mock_fund_repo, mock_perf_repo, fund):
        """take_daily_snapshot stores the correct vrednost_fonda, profit, and likvidna_sredstva."""
        valuation = MagicMock()
        valuation.compute_vrednost_fonda = AsyncMock(return_value=Decimal("60000.00"))
        valuation.compute_profit = AsyncMock(return_value=Decimal("10000.00"))
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        await svc.take_daily_snapshot()
        # upsert_snapshot(fund_id, date, vrednost_fonda, profit, likvidna_sredstva)
        call_args = mock_perf_repo.upsert_snapshot.call_args.args
        assert call_args[2] == Decimal("60000.00")
        assert call_args[3] == Decimal("10000.00")
        assert call_args[4] == fund.likvidna_sredstva

    async def test_snapshot_date_is_today(self, mock_fund_repo, mock_perf_repo):
        """take_daily_snapshot records today's date on each snapshot."""
        valuation = MagicMock()
        valuation.compute_vrednost_fonda = AsyncMock(return_value=Decimal("10000.00"))
        valuation.compute_profit = AsyncMock(return_value=Decimal("0.00"))
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        await svc.take_daily_snapshot()
        call_args = mock_perf_repo.upsert_snapshot.call_args.args
        assert call_args[1] == date.today()

    async def test_snapshot_continues_when_one_fund_raises(self, mock_fund_repo, mock_perf_repo, fund):
        """take_daily_snapshot skips a failing fund and continues to the next one."""
        second_fund = MagicMock()
        second_fund.id = 2
        second_fund.likvidna_sredstva = Decimal("1000.00")
        mock_fund_repo.find_all = AsyncMock(return_value=[fund, second_fund])
        valuation = MagicMock()
        valuation.compute_vrednost_fonda = AsyncMock(side_effect=[Exception("order-service down"), Decimal("1000.00")])
        valuation.compute_profit = AsyncMock(return_value=Decimal("0.00"))
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        await svc.take_daily_snapshot()
        mock_perf_repo.upsert_snapshot.assert_awaited_once()

    async def test_snapshot_no_funds_saves_nothing(self, mock_fund_repo, mock_perf_repo):
        """take_daily_snapshot does not call upsert_snapshot when there are no funds."""
        mock_fund_repo.find_all = AsyncMock(return_value=[])
        valuation = MagicMock()
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        await svc.take_daily_snapshot()
        mock_perf_repo.upsert_snapshot.assert_not_awaited()


class TestGetPerformance:
    """Tests for FundPerformanceService.get_performance."""

    async def test_month_period_queries_30_days_back(self, mock_fund_repo, mock_perf_repo, snapshot):
        """get_performance with MONTH period queries snapshots from 30 days ago."""
        valuation = MagicMock()
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        expected_since = date.today() - timedelta(days=30)
        await svc.get_performance(1, PerformancePeriod.MONTH)
        mock_perf_repo.find_by_fund_and_period.assert_awaited_once_with(1, expected_since)

    async def test_quarter_period_queries_90_days_back(self, mock_fund_repo, mock_perf_repo, snapshot):
        """get_performance with QUARTER period queries snapshots from 90 days ago."""
        valuation = MagicMock()
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        expected_since = date.today() - timedelta(days=90)
        await svc.get_performance(1, PerformancePeriod.QUARTER)
        mock_perf_repo.find_by_fund_and_period.assert_awaited_once_with(1, expected_since)

    async def test_year_period_queries_365_days_back(self, mock_fund_repo, mock_perf_repo, snapshot):
        """get_performance with YEAR period queries snapshots from 365 days ago."""
        valuation = MagicMock()
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        expected_since = date.today() - timedelta(days=365)
        await svc.get_performance(1, PerformancePeriod.YEAR)
        mock_perf_repo.find_by_fund_and_period.assert_awaited_once_with(1, expected_since)

    async def test_get_performance_returns_repo_results(self, mock_fund_repo, mock_perf_repo, snapshot):
        """get_performance returns exactly what the repository returns."""
        valuation = MagicMock()
        svc = _make_svc(mock_fund_repo, mock_perf_repo, valuation)
        result = await svc.get_performance(1, PerformancePeriod.MONTH)
        assert result == [snapshot]

    async def test_period_to_days_mapping(self):
        """_period_to_days returns the correct integer for each PerformancePeriod."""
        assert FundPerformanceService._period_to_days(PerformancePeriod.MONTH) == 30
        assert FundPerformanceService._period_to_days(PerformancePeriod.QUARTER) == 90
        assert FundPerformanceService._period_to_days(PerformancePeriod.YEAR) == 365
