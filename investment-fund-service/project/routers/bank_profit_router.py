"""Router exposing bank profit portal endpoints (supervisor only)."""

from typing import Annotated, List

from fastapi import APIRouter, Depends, Request

from project.dependencies import get_client_fund_position_repository, get_fund_valuation_service, get_investment_fund_repository, require_supervisor
from project.middleware.token_data import TokenData
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.bank_fund_position_item import BankFundPositionItem
from project.services.fund_valuation_service import FundValuationService

BANK_KLIJENT_ID = -1


class BankProfitRouter:
    """Registers /bank-profit read routes and exposes them via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register all bank profit route handlers."""
        self._router = APIRouter(prefix="/bank-profit", tags=["bank-profit"])
        self._router.get("/fund-positions", response_model=List[BankFundPositionItem])(self.fund_positions)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def fund_positions(self, _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request, fund_repo: Annotated[InvestmentFundRepository, Depends(get_investment_fund_repository)], position_repo: Annotated[ClientFundPositionRepository, Depends(get_client_fund_position_repository)], valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)]) -> List[BankFundPositionItem]:
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
            items.append(BankFundPositionItem(fund_id=fund.id, naziv=fund.naziv, menadzer_id=fund.menadzer_id, bank_procenat=procenat, bank_vrednost=bank_vrednost, bank_profit=bank_profit))
        return items
