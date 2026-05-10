"""Unit tests for FundInvestmentService."""

from decimal import Decimal
from unittest.mock import AsyncMock

import pytest
from fastapi import HTTPException

from project.enums.transaction_status import TransactionStatus
from project.schemas.invest_request import InvestRequest
from project.services.fund_investment_service import FundInvestmentService


class TestFundInvestmentServiceHappyPath:
    """Tests for the successful investment deposit flow."""

    async def test_invest_creates_completed_transaction(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund):
        """A successful invest call ends with a COMPLETED transaction."""
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        result = await svc.invest(1, 42, request, "token")
        assert result.status == TransactionStatus.COMPLETED

    async def test_invest_calls_banking_transfer(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest triggers exactly one transfer call on the banking client."""
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        await svc.invest(1, 42, request, "token")
        mock_banking_client.transfer.assert_awaited_once()

    async def test_invest_updates_liquidity_and_position(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest updates the fund's likvidna_sredstva and upserts the client position."""
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        await svc.invest(1, 42, request, "token")
        mock_fund_repo.update_likvidna_sredstva.assert_awaited_once_with(1, Decimal("2000.00"))
        mock_position_repo.upsert.assert_awaited_once_with(42, 1, Decimal("2000.00"))

    async def test_invest_assigns_generated_idempotency_key_when_not_provided(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest generates a UUID idempotency key when the request omits one."""
        saved = []
        mock_tx_repo.save = AsyncMock(side_effect=lambda t: saved.append(t) or t)
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        await svc.invest(1, 42, request, "token")
        assert saved[0].idempotency_key is not None
        assert len(saved[0].idempotency_key) == 36


class TestFundInvestmentServiceIdempotency:
    """Tests for idempotency key behaviour."""

    async def test_invest_returns_existing_transaction_on_duplicate_key(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, transaction):
        """invest returns the original transaction without calling banking when the key is duplicate."""
        mock_tx_repo.find_by_idempotency_key = AsyncMock(return_value=transaction)
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7, idempotency_key="key-abc")
        result = await svc.invest(1, 42, request, "token")
        assert result is transaction
        mock_banking_client.transfer.assert_not_awaited()

    async def test_invest_uses_provided_idempotency_key(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest stores the caller-supplied idempotency key on the transaction."""
        saved = []
        mock_tx_repo.save = AsyncMock(side_effect=lambda t: saved.append(t) or t)
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7, idempotency_key="my-custom-key")
        await svc.invest(1, 42, request, "token")
        assert saved[0].idempotency_key == "my-custom-key"


class TestFundInvestmentServiceValidation:
    """Tests for invest input validation."""

    async def test_invest_raises_404_when_fund_not_found(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest raises 404 when the specified fund does not exist."""
        mock_fund_repo.find_by_id = AsyncMock(return_value=None)
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        with pytest.raises(HTTPException) as exc_info:
            await svc.invest(999, 42, request, "token")
        assert exc_info.value.status_code == 404

    async def test_invest_raises_400_when_below_minimum(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund):
        """invest raises 400 when the amount is less than the fund's minimalni_ulog."""
        fund.minimalni_ulog = Decimal("1000.00")
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("500.00"), source_account_id=7)
        with pytest.raises(HTTPException) as exc_info:
            await svc.invest(1, 42, request, "token")
        assert exc_info.value.status_code == 400

    async def test_invest_accepts_exactly_minimum_amount(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client, fund):
        """invest succeeds when the amount equals exactly the minimalni_ulog."""
        fund.minimalni_ulog = Decimal("1000.00")
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("1000.00"), source_account_id=7)
        result = await svc.invest(1, 42, request, "token")
        assert result.status == TransactionStatus.COMPLETED


class TestFundInvestmentServiceBankingFailure:
    """Tests for banking client failure handling."""

    async def test_invest_marks_transaction_failed_on_banking_error(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest marks the transaction FAILED and raises 502 when the banking transfer fails."""
        mock_banking_client.transfer = AsyncMock(side_effect=Exception("network error"))
        statuses = []
        mock_tx_repo.save = AsyncMock(side_effect=lambda t: statuses.append(t.status) or t)
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        with pytest.raises(HTTPException) as exc_info:
            await svc.invest(1, 42, request, "token")
        assert exc_info.value.status_code == 502
        assert TransactionStatus.FAILED in statuses

    async def test_invest_does_not_update_position_on_banking_error(self, mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client):
        """invest does not update the client position when the banking transfer fails."""
        mock_banking_client.transfer = AsyncMock(side_effect=Exception("timeout"))
        svc = FundInvestmentService(mock_fund_repo, mock_tx_repo, mock_position_repo, mock_banking_client)
        request = InvestRequest(iznos=Decimal("2000.00"), source_account_id=7)
        with pytest.raises(HTTPException):
            await svc.invest(1, 42, request, "token")
        mock_position_repo.upsert.assert_not_awaited()
