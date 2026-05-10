"""Unit tests for InvestmentFundService."""

from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import HTTPException

from project.schemas.create_fund_request import CreateFundRequest
from project.schemas.update_fund_request import UpdateFundRequest
from project.services.investment_fund_service import InvestmentFundService


class TestInvestmentFundServiceCreateFund:
    """Tests for InvestmentFundService.create_fund."""

    async def test_create_fund_happy_path(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """Fund is created when naziv is unique, manager is supervisor, and banking succeeds."""
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = CreateFundRequest(naziv="Alpha Fund", minimalni_ulog=Decimal("1000"), menadzer_id=10)
        result = await svc.create_fund(request, "token")
        mock_fund_repo.save.assert_awaited_once()
        assert result is not None

    async def test_create_fund_duplicate_naziv_raises_409(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """create_fund raises 409 when a fund with the same naziv already exists."""
        mock_fund_repo.find_by_naziv = AsyncMock(return_value=fund)
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = CreateFundRequest(naziv="Alpha Fund", minimalni_ulog=Decimal("1000"), menadzer_id=10)
        with pytest.raises(HTTPException) as exc_info:
            await svc.create_fund(request, "token")
        assert exc_info.value.status_code == 409

    async def test_create_fund_non_supervisor_raises_400(self, mock_fund_repo, mock_banking_client, mock_employee_client):
        """create_fund raises 400 when the specified manager is not a supervisor."""
        mock_employee_client.is_supervisor = AsyncMock(return_value=False)
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = CreateFundRequest(naziv="Beta Fund", minimalni_ulog=Decimal("500"), menadzer_id=99)
        with pytest.raises(HTTPException) as exc_info:
            await svc.create_fund(request, "token")
        assert exc_info.value.status_code == 400

    async def test_create_fund_banking_returns_no_account_id_raises_502(self, mock_fund_repo, mock_banking_client, mock_employee_client):
        """create_fund raises 502 when the banking service returns no account ID."""
        mock_banking_client.create_fund_account = AsyncMock(return_value={})
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = CreateFundRequest(naziv="Gamma Fund", minimalni_ulog=Decimal("100"), menadzer_id=10)
        with pytest.raises(HTTPException) as exc_info:
            await svc.create_fund(request, "token")
        assert exc_info.value.status_code == 502

    async def test_create_fund_uses_account_id_from_banking_response(self, mock_fund_repo, mock_banking_client, mock_employee_client):
        """The account_id saved on the fund matches what the banking service returned."""
        mock_banking_client.create_fund_account = AsyncMock(return_value={"id": 777})
        saved_funds = []
        mock_fund_repo.save = AsyncMock(side_effect=lambda f: saved_funds.append(f) or f)
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = CreateFundRequest(naziv="Delta Fund", minimalni_ulog=Decimal("100"), menadzer_id=10)
        await svc.create_fund(request, "token")
        assert saved_funds[0].account_id == 777


class TestInvestmentFundServiceGetAndList:
    """Tests for InvestmentFundService.get_fund and list_funds."""

    async def test_list_funds_returns_all(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """list_funds returns all funds from the repository."""
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        result = await svc.list_funds()
        assert result == [fund]

    async def test_get_fund_returns_fund_when_found(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """get_fund returns the fund when it exists."""
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        result = await svc.get_fund(1)
        assert result is fund

    async def test_get_fund_raises_404_when_not_found(self, mock_fund_repo, mock_banking_client, mock_employee_client):
        """get_fund raises 404 when no fund with that ID exists."""
        mock_fund_repo.find_by_id = AsyncMock(return_value=None)
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        with pytest.raises(HTTPException) as exc_info:
            await svc.get_fund(999)
        assert exc_info.value.status_code == 404


class TestInvestmentFundServiceUpdateFund:
    """Tests for InvestmentFundService.update_fund."""

    async def test_update_fund_opis(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """update_fund sets a new opis on the fund."""
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = UpdateFundRequest(opis="New description")
        result = await svc.update_fund(1, request, "token")
        assert result.opis == "New description"

    async def test_update_fund_minimalni_ulog(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """update_fund applies a new minimalni_ulog."""
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = UpdateFundRequest(minimalni_ulog=Decimal("2500.00"))
        result = await svc.update_fund(1, request, "token")
        assert result.minimalni_ulog == Decimal("2500.00")

    async def test_update_fund_menadzer_validates_supervisor(self, mock_fund_repo, mock_banking_client, mock_employee_client):
        """update_fund raises 400 when the new manager is not a supervisor."""
        mock_employee_client.is_supervisor = AsyncMock(return_value=False)
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        request = UpdateFundRequest(menadzer_id=55)
        with pytest.raises(HTTPException) as exc_info:
            await svc.update_fund(1, request, "token")
        assert exc_info.value.status_code == 400

    async def test_update_fund_not_found_raises_404(self, mock_fund_repo, mock_banking_client, mock_employee_client):
        """update_fund propagates the 404 raised by get_fund when the fund does not exist."""
        mock_fund_repo.find_by_id = AsyncMock(return_value=None)
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        with pytest.raises(HTTPException) as exc_info:
            await svc.update_fund(999, UpdateFundRequest(opis="x"), "token")
        assert exc_info.value.status_code == 404

    async def test_update_fund_no_fields_leaves_fund_unchanged(self, mock_fund_repo, mock_banking_client, mock_employee_client, fund):
        """Passing an empty UpdateFundRequest leaves all fund fields at their original values."""
        original_opis = fund.opis
        original_minimalni_ulog = fund.minimalni_ulog
        svc = InvestmentFundService(mock_fund_repo, mock_banking_client, mock_employee_client)
        result = await svc.update_fund(1, UpdateFundRequest(), "token")
        assert result.opis == original_opis
        assert result.minimalni_ulog == original_minimalni_ulog
