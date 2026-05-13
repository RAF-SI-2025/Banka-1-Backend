"""Router class exposing CRUD endpoints for investment funds."""

from typing import Annotated, List

from fastapi import APIRouter, Depends, Request

from project.dependencies import get_fund_valuation_service, get_investment_fund_service, require_authenticated, require_supervisor
from project.middleware.token_data import TokenData
from project.schemas.create_fund_request import CreateFundRequest
from project.schemas.fund_detail_response import FundDetailResponse
from project.schemas.fund_response import FundResponse
from project.schemas.update_fund_request import UpdateFundRequest
from project.services.fund_valuation_service import FundValuationService
from project.services.investment_fund_service import InvestmentFundService


class FundRouter:
    """Registers /funds CRUD routes on an APIRouter and exposes it via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register all route handlers."""
        self._router = APIRouter(prefix="/funds", tags=["funds"])
        self._router.get("/", response_model=List[FundResponse])(self.list_funds)
        self._router.post("/", response_model=FundResponse, status_code=201)(self.create_fund)
        self._router.get("/{fund_id}", response_model=FundDetailResponse)(self.get_fund_detail)
        self._router.patch("/{fund_id}", response_model=FundResponse)(self.update_fund)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def list_funds(self, service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)], _token: Annotated[TokenData, Depends(require_authenticated)]) -> List[FundResponse]:
        """Return all investment funds as a summary list."""
        funds = await service.list_funds()
        return [FundResponse.model_validate(f) for f in funds]

    async def create_fund(self, request: CreateFundRequest, service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)], _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request) -> FundResponse:
        """Create a new investment fund (supervisor only); opens a banking account automatically."""
        bearer = raw_request.state.raw_token
        fund = await service.create_fund(request, bearer)
        return FundResponse.model_validate(fund)

    async def get_fund_detail(self, fund_id: int, service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)], valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)], _token: Annotated[TokenData, Depends(require_authenticated)], raw_request: Request) -> FundDetailResponse:
        """Return fund details including computed vrednost_fonda and profit."""
        fund = await service.get_fund(fund_id)
        bearer = raw_request.state.raw_token
        vrednost_fonda = await valuation.get_cached_or_compute_vrednost(fund_id, fund, bearer)
        profit = await valuation.compute_profit(fund, vrednost_fonda)
        return FundDetailResponse(id=fund.id, naziv=fund.naziv, opis=fund.opis, minimalni_ulog=fund.minimalni_ulog, menadzer_id=fund.menadzer_id, likvidna_sredstva=fund.likvidna_sredstva, account_id=fund.account_id, datum_kreiranja=fund.datum_kreiranja, vrednost_fonda=vrednost_fonda, profit=profit)

    async def update_fund(self, fund_id: int, request: UpdateFundRequest, service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)], _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request) -> FundResponse:
        """Partially update a fund's description, minimum investment, or manager (supervisor only)."""
        bearer = raw_request.state.raw_token
        fund = await service.update_fund(fund_id, request, bearer)
        return FundResponse.model_validate(fund)
