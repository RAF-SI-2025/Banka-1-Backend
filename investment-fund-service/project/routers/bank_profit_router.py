"""Router exposing bank profit portal endpoints (supervisor only)."""

import logging
from decimal import Decimal
from typing import Annotated, List

from fastapi import APIRouter, Depends, Request

from project.clients.employee_client import EmployeeClient
from project.constants import BANK_KLIJENT_ID
from project.dependencies import (
    get_client_fund_position_repository,
    get_employee_client,
    get_fund_investment_service,
    get_fund_redemption_service,
    get_fund_valuation_service,
    get_investment_fund_repository,
    get_settings_dep,
    require_supervisor,
)
from project.config.settings import Settings
from project.middleware.token_data import TokenData
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.actuary_performance_item import ActuaryPerformanceItem
from project.schemas.bank_fund_position_item import BankFundPositionItem
from project.schemas.invest_request import InvestRequest
from project.schemas.invest_response import InvestResponse
from project.schemas.redeem_request import RedeemRequest
from project.schemas.redeem_response import RedeemResponse
from project.services.fund_investment_service import FundInvestmentService
from project.services.fund_redemption_service import FundRedemptionService
from project.services.fund_valuation_service import FundValuationService

logger = logging.getLogger(__name__)


class BankProfitRouter:
    """Registers /bank-profit routes and exposes them via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register all bank profit route handlers."""
        self._router = APIRouter(prefix="/bank-profit", tags=["bank-profit"])
        self._router.get("/fund-positions", response_model=List[BankFundPositionItem])(self.fund_positions)
        self._router.get("/actuary-performances", response_model=List[ActuaryPerformanceItem])(self.actuary_performances)
        self._router.post("/funds/{fund_id}/invest", response_model=InvestResponse, status_code=201)(self.bank_invest)
        self._router.post("/funds/{fund_id}/redeem", response_model=RedeemResponse)(self.bank_redeem)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def fund_positions(self, _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request, fund_repo: Annotated[InvestmentFundRepository, Depends(get_investment_fund_repository)], position_repo: Annotated[ClientFundPositionRepository, Depends(get_client_fund_position_repository)], valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)], employee: Annotated[EmployeeClient, Depends(get_employee_client)]) -> List[BankFundPositionItem]:
        """Return the bank's ownership snapshot across all investment funds (supervisor only)."""
        bearer = raw_request.state.raw_token
        funds = await fund_repo.find_all()
        items = []
        for fund in funds:
            bank_position = await position_repo.find_by_klijent_and_fund(BANK_KLIJENT_ID, fund.id)
            if bank_position is None:
                continue
            vrednost_fonda = await valuation.get_cached_or_compute_vrednost(fund.id, fund, bearer)
            procenat = await valuation.compute_procenat_fonda(BANK_KLIJENT_ID, fund.id)
            bank_vrednost = procenat * vrednost_fonda
            bank_profit = bank_vrednost - bank_position.ukupan_ulozeni_iznos
            menadzer_ime: str | None = None
            menadzer_prezime: str | None = None
            try:
                mgr = await employee.get_employee(fund.menadzer_id, bearer)
                menadzer_ime = mgr.get("firstName") or mgr.get("ime") or mgr.get("first_name")
                menadzer_prezime = mgr.get("lastName") or mgr.get("prezime") or mgr.get("last_name")
            except Exception as exc:
                logger.warning("Could not fetch manager %s for fund %s: %s", fund.menadzer_id, fund.id, exc)
            items.append(BankFundPositionItem(fund_id=fund.id, naziv=fund.naziv, menadzer_id=fund.menadzer_id, menadzer_ime=menadzer_ime, menadzer_prezime=menadzer_prezime, bank_procenat=procenat, bank_vrednost=bank_vrednost, bank_profit=bank_profit))
        return items

    async def actuary_performances(self, _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request, fund_repo: Annotated[InvestmentFundRepository, Depends(get_investment_fund_repository)], position_repo: Annotated[ClientFundPositionRepository, Depends(get_client_fund_position_repository)], valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)], employee: Annotated[EmployeeClient, Depends(get_employee_client)]) -> List[ActuaryPerformanceItem]:
        """Return all actuaries (SUPERVISOR, ADMIN, AGENT) with their aggregated fund profits (supervisor only)."""
        bearer = raw_request.state.raw_token
        try:
            all_employees = await employee.get_all_employees(bearer)
        except Exception as exc:
            logger.error("Failed to fetch employees for actuary performances: %s", exc)
            all_employees = []

        actuary_roles = {"SUPERVISOR", "ADMIN", "AGENT"}
        actuaries = [e for e in all_employees if e.get("role", "") in actuary_roles]

        funds = await fund_repo.find_all()
        fund_profits: dict[int, Decimal] = {}
        for fund in funds:
            try:
                vrednost = await valuation.get_cached_or_compute_vrednost(fund.id, fund, bearer)
                profit = await valuation.compute_profit(fund, vrednost)
                fund_profits[fund.menadzer_id] = fund_profits.get(fund.menadzer_id, Decimal("0")) + profit
            except Exception as exc:
                logger.warning("Could not compute profit for fund %s: %s", fund.id, exc)

        items = []
        for actuary in actuaries:
            actuary_id = actuary.get("id") or actuary.get("employeeId")
            if actuary_id is None:
                continue
            actuary_id = int(actuary_id)
            profit = fund_profits.get(actuary_id, Decimal("0"))
            items.append(ActuaryPerformanceItem(
                id=actuary_id,
                ime=actuary.get("firstName") or actuary.get("ime") or actuary.get("first_name"),
                prezime=actuary.get("lastName") or actuary.get("prezime") or actuary.get("last_name"),
                role=actuary.get("role"),
                profit=profit,
            ))
        return items

    async def bank_invest(self, fund_id: int, request: InvestRequest, service: Annotated[FundInvestmentService, Depends(get_fund_investment_service)], _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request) -> InvestResponse:
        """Deposit from a bank account into a fund on behalf of the bank (supervisor only)."""
        bearer = raw_request.state.raw_token
        tx = await service.invest(fund_id, BANK_KLIJENT_ID, request, bearer, is_supervisor=True)
        return InvestResponse(transaction_id=tx.id, status=tx.status, iznos=tx.iznos, fund_id=tx.fund_id, klijent_id=tx.klijent_id, timestamp=tx.timestamp)

    async def bank_redeem(self, fund_id: int, request: RedeemRequest, service: Annotated[FundRedemptionService, Depends(get_fund_redemption_service)], _token: Annotated[TokenData, Depends(require_supervisor)], settings: Annotated[Settings, Depends(get_settings_dep)], raw_request: Request) -> RedeemResponse:
        """Withdraw bank's position from a fund (supervisor only; no commission)."""
        bearer = raw_request.state.raw_token
        tx, liquidation_started = await service.redeem(fund_id, BANK_KLIJENT_ID, request, bearer, is_supervisor=True, commission_rate=0.0)
        return RedeemResponse(transaction_id=tx.id, status=tx.status, iznos=tx.iznos, fund_id=tx.fund_id, klijent_id=tx.klijent_id, liquidation_started=liquidation_started, timestamp=tx.timestamp)
