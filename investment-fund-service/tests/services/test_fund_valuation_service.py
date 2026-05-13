"""Unit tests for FundValuationService."""

from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

import pytest

from project.services.fund_valuation_service import FundValuationService


def _make_svc(fund_repo, position_repo, tx_repo, order_client, redis_client=None) -> FundValuationService:
    """Construct a FundValuationService with the given dependencies."""
    return FundValuationService(fund_repo, position_repo, tx_repo, order_client, redis_client)


class TestComputeVrednostFonda:
    """Tests for FundValuationService.compute_vrednost_fonda."""

    async def test_no_holdings_returns_likvidna_sredstva(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """vrednost_fonda equals likvidna_sredstva when the portfolio is empty."""
        mock_order_client.get_fund_portfolio = AsyncMock(return_value=[])
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_vrednost_fonda(fund, "token")
        assert result == fund.likvidna_sredstva

    async def test_holdings_add_to_likvidna_sredstva(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """vrednost_fonda = likvidna_sredstva + sum of (price * quantity) for each holding."""
        mock_order_client.get_fund_portfolio = AsyncMock(return_value=[{"currentPrice": "100.00", "quantity": 100}])
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_vrednost_fonda(fund, "token")
        assert result == fund.likvidna_sredstva + Decimal("10000.00")

    async def test_multiple_holdings_summed_correctly(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """vrednost_fonda sums across all holdings."""
        holdings = [{"currentPrice": "200.00", "quantity": 10}, {"currentPrice": "50.00", "quantity": 40}]
        mock_order_client.get_fund_portfolio = AsyncMock(return_value=holdings)
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_vrednost_fonda(fund, "token")
        assert result == fund.likvidna_sredstva + Decimal("4000.00")

    async def test_order_client_failure_falls_back_to_likvidna_sredstva(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """vrednost_fonda falls back to likvidna_sredstva when order-service is unavailable."""
        mock_order_client.get_fund_portfolio = AsyncMock(side_effect=Exception("timeout"))
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_vrednost_fonda(fund, "token")
        assert result == fund.likvidna_sredstva


class TestComputeProfit:
    """Tests for FundValuationService.compute_profit."""

    async def test_profit_is_value_minus_invested(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """profit = vrednost_fonda - total invested across all clients."""
        mock_tx_repo.sum_inflows_by_fund = AsyncMock(return_value=Decimal("40000.00"))
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_profit(fund, Decimal("60000.00"))
        assert result == Decimal("20000.00")

    async def test_profit_can_be_negative(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """profit is negative when the fund has lost value relative to total invested."""
        mock_tx_repo.sum_inflows_by_fund = AsyncMock(return_value=Decimal("70000.00"))
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_profit(fund, Decimal("60000.00"))
        assert result == Decimal("-10000.00")

    async def test_profit_zero_when_value_equals_invested(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """profit is exactly zero when the fund value equals total invested."""
        mock_tx_repo.sum_inflows_by_fund = AsyncMock(return_value=Decimal("60000.00"))
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_profit(fund, Decimal("60000.00"))
        assert result == Decimal("0")


class TestComputeProcenatFonda:
    """Tests for FundValuationService.compute_procenat_fonda."""

    async def test_single_client_owns_100_percent(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, position):
        """A client who is the only investor owns 100% of the fund."""
        mock_position_repo.sum_ulozeni_iznos_by_fund = AsyncMock(return_value=Decimal("5000.00"))
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        mock_position_repo.find_by_klijent_and_fund = AsyncMock(return_value=position)
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_procenat_fonda(42, 1)
        assert result == Decimal("1")

    async def test_client_owns_correct_fraction(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, position):
        """A client who invested half the total owns 50% of the fund."""
        mock_position_repo.sum_ulozeni_iznos_by_fund = AsyncMock(return_value=Decimal("10000.00"))
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        mock_position_repo.find_by_klijent_and_fund = AsyncMock(return_value=position)
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_procenat_fonda(42, 1)
        assert result == Decimal("0.5")

    async def test_returns_zero_when_no_clients_invested(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client):
        """procenat_fonda returns 0 when no one has invested in the fund yet."""
        mock_position_repo.sum_ulozeni_iznos_by_fund = AsyncMock(return_value=Decimal("0"))
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_procenat_fonda(42, 1)
        assert result == Decimal("0")

    async def test_returns_zero_when_client_has_no_position(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client):
        """procenat_fonda returns 0 when the client has no position in the fund."""
        mock_position_repo.sum_ulozeni_iznos_by_fund = AsyncMock(return_value=Decimal("5000.00"))
        mock_position_repo.find_by_klijent_and_fund = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client)
        result = await svc.compute_procenat_fonda(99, 1)
        assert result == Decimal("0")


class TestRedisCaching:
    """Tests for FundValuationService.get_cached_or_compute_vrednost."""

    async def test_cache_hit_returns_cached_value_without_recomputing(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """get_cached_or_compute_vrednost returns cached value and skips order-service call."""
        redis_mock = MagicMock()
        redis_mock.get = AsyncMock(return_value=b"99999.00")
        redis_mock.set = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, redis_mock)
        result = await svc.get_cached_or_compute_vrednost(1, fund, "token")
        assert result == Decimal("99999.00")
        mock_order_client.get_fund_portfolio.assert_not_awaited()

    async def test_cache_miss_computes_and_stores_value(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """get_cached_or_compute_vrednost computes the value and writes it to Redis on a cache miss."""
        mock_order_client.get_fund_portfolio = AsyncMock(return_value=[])
        redis_mock = MagicMock()
        redis_mock.get = AsyncMock(return_value=None)
        redis_mock.set = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, redis_mock)
        result = await svc.get_cached_or_compute_vrednost(1, fund, "token")
        assert result == fund.likvidna_sredstva
        redis_mock.set.assert_awaited_once()

    async def test_no_redis_computes_without_caching(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """get_cached_or_compute_vrednost computes normally when Redis is not configured."""
        mock_order_client.get_fund_portfolio = AsyncMock(return_value=[])
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, None)
        result = await svc.get_cached_or_compute_vrednost(1, fund, "token")
        assert result == fund.likvidna_sredstva

    async def test_redis_error_falls_back_to_compute(self, mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, fund):
        """get_cached_or_compute_vrednost falls back to computing when Redis raises an exception."""
        mock_order_client.get_fund_portfolio = AsyncMock(return_value=[])
        redis_mock = MagicMock()
        redis_mock.get = AsyncMock(side_effect=Exception("redis down"))
        redis_mock.set = AsyncMock(side_effect=Exception("redis down"))
        svc = _make_svc(mock_fund_repo, mock_position_repo, mock_tx_repo, mock_order_client, redis_mock)
        result = await svc.get_cached_or_compute_vrednost(1, fund, "token")
        assert result == fund.likvidna_sredstva
