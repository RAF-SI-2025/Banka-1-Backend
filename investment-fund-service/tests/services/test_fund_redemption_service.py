"""Unit tests for FundRedemptionService."""

from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import HTTPException

from project.enums.transaction_status import TransactionStatus
from project.schemas.redeem_request import RedeemRequest
from project.services.fund_redemption_service import FundRedemptionService


def _make_svc(fund_repo, tx_repo, position_repo, banking_client, liquidation_service=None, valuation_service=None) -> FundRedemptionService:
    """Construct FundRedemptionService with default no-op mocks for optional dependencies."""
    if liquidation_service is None:
        liquidation_service = MagicMock()
        liquidation_service.start_liquidation = AsyncMock(return_value=None)
    if valuation_service is None:
        valuation_service = MagicMock()
        valuation_service.get_cached_or_compute_vrednost = AsyncMock(return_value=Decimal("50000.00"))
        valuation_service.compute_procenat_fonda = AsyncMock(return_value=Decimal("0.1"))
    return FundRedemptionService(fund_repo, tx_repo, position_repo, banking_client, liquidation_service, valuation_service)


class TestFundRedemptionServiceCoveredLiquidity:
    """Tests for the direct-transfer path when fund liquidity covers the withdrawal."""

    async def test_redeem_covered_returns_completed_transaction(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem returns a COMPLETED transaction when the fund has sufficient liquid assets."""
        fund.likvidna_sredstva = Decimal("50000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        tx, liquidation_started = await svc.redeem(1, 42, request, "token")
        assert tx.status == TransactionStatus.COMPLETED
        assert liquidation_started is False

    async def test_redeem_covered_calls_banking_transfer(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem triggers a banking transfer on the covered path."""
        fund.likvidna_sredstva = Decimal("50000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        await svc.redeem(1, 42, request, "token")
        mock_banking_client.transfer.assert_awaited_once()

    async def test_redeem_covered_decrements_liquidity(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem decrements fund liquidity by the withdrawn amount on success."""
        fund.likvidna_sredstva = Decimal("50000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        await svc.redeem(1, 42, request, "token")
        mock_fund_repo.update_likvidna_sredstva.assert_awaited_once_with(1, Decimal("-3000.00"))

    async def test_redeem_covered_upserts_position(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem decrements the client position by the withdrawn amount."""
        fund.likvidna_sredstva = Decimal("50000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        await svc.redeem(1, 42, request, "token")
        mock_position_repo.upsert.assert_awaited_once_with(42, 1, Decimal("-3000.00"))


class TestFundRedemptionServiceUncoveredLiquidity:
    """Tests for the SAGA path when liquid assets are insufficient."""

    async def test_redeem_uncovered_returns_pending_and_liquidation_flag(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem returns PENDING transaction and liquidation_started=True when liquidity is short."""
        fund.likvidna_sredstva = Decimal("1000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        liquidation_svc = MagicMock()
        liquidation_svc.start_liquidation = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, liquidation_svc)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        tx, liquidation_started = await svc.redeem(1, 42, request, "token")
        assert tx.status == TransactionStatus.PENDING
        assert liquidation_started is True

    async def test_redeem_uncovered_calls_start_liquidation(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem delegates to FundLiquidationService.start_liquidation on the SAGA path."""
        fund.likvidna_sredstva = Decimal("1000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        liquidation_svc = MagicMock()
        liquidation_svc.start_liquidation = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, liquidation_svc)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        await svc.redeem(1, 42, request, "token")
        liquidation_svc.start_liquidation.assert_awaited_once()

    async def test_redeem_uncovered_does_not_call_banking_transfer(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem does not call the banking transfer directly when SAGA is started."""
        fund.likvidna_sredstva = Decimal("1000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7)
        await svc.redeem(1, 42, request, "token")
        mock_banking_client.transfer.assert_not_awaited()


class TestFundRedemptionServiceValidation:
    """Tests for redeem input validation."""

    async def test_redeem_raises_404_when_fund_not_found(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """redeem raises 404 when the specified fund does not exist."""
        mock_fund_repo.find_by_id = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        with pytest.raises(HTTPException) as exc_info:
            await svc.redeem(999, 42, RedeemRequest(iznos=Decimal("100"), destination_account_id=7), "token")
        assert exc_info.value.status_code == 404

    async def test_redeem_raises_400_when_no_position(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """redeem raises 400 when the client has no position in the fund."""
        mock_position_repo.find_by_klijent_and_fund = AsyncMock(return_value=None)
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        with pytest.raises(HTTPException) as exc_info:
            await svc.redeem(1, 42, RedeemRequest(iznos=Decimal("100"), destination_account_id=7), "token")
        assert exc_info.value.status_code == 400

    async def test_redeem_raises_400_when_insufficient_position(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, position):
        """redeem raises 400 when current market value is less than the requested amount."""
        position.ukupan_ulozeni_iznos = Decimal("500.00")
        valuation_svc = MagicMock()
        valuation_svc.get_cached_or_compute_vrednost = AsyncMock(return_value=Decimal("5000.00"))
        valuation_svc.compute_procenat_fonda = AsyncMock(return_value=Decimal("0.1"))
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, valuation_service=valuation_svc)
        with pytest.raises(HTTPException) as exc_info:
            await svc.redeem(1, 42, RedeemRequest(iznos=Decimal("1000.00"), destination_account_id=7), "token")
        assert exc_info.value.status_code == 400

    async def test_redeem_raises_502_when_banking_transfer_fails(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund, position):
        """redeem raises 502 and marks the transaction FAILED when the banking transfer throws."""
        fund.likvidna_sredstva = Decimal("50000.00")
        position.ukupan_ulozeni_iznos = Decimal("5000.00")
        mock_banking_client.transfer = AsyncMock(side_effect=Exception("bank down"))
        statuses = []
        mock_tx_repo.save = AsyncMock(side_effect=lambda t: statuses.append(t.status) or t)
        svc = _make_svc(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        with pytest.raises(HTTPException) as exc_info:
            await svc.redeem(1, 42, RedeemRequest(iznos=Decimal("3000.00"), destination_account_id=7), "token")
        assert exc_info.value.status_code == 502
        assert TransactionStatus.FAILED in statuses
