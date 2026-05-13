"""Router class exposing CRUD endpoints for investment funds."""

import asyncio
import logging
from decimal import Decimal
from typing import Annotated, List, Optional

from fastapi import APIRouter, Depends, Query, Request

from project.clients.employee_client import EmployeeClient
from project.clients.order_client import OrderClient
from project.dependencies import get_employee_client, get_fund_valuation_service, get_investment_fund_service, get_order_client, require_authenticated, require_supervisor
from project.middleware.token_data import TokenData
from project.schemas.create_fund_request import CreateFundRequest
from project.schemas.fund_detail_response import FundDetailResponse
from project.schemas.fund_response import FundResponse
from project.schemas.security_item import SecurityItem
from project.schemas.update_fund_request import UpdateFundRequest
from project.services.fund_valuation_service import FundValuationService
from project.services.investment_fund_service import InvestmentFundService

logger = logging.getLogger(__name__)


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

    async def list_funds(
        self,
        service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)],
        valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)],
        token: Annotated[TokenData, Depends(require_authenticated)],
        raw_request: Request,
        name_contains: Optional[str] = Query(None, description="Filter by fund name (case-insensitive substring)"),
        sort_by: Optional[str] = Query(None, description="Sort field: naziv, minimalni_ulog, likvidna_sredstva, datum_kreiranja"),
        sort_order: str = Query("asc", description="Sort direction: asc or desc"),
        my_funds: bool = Query(False, description="If true and caller is SUPERVISOR/ADMIN, return only funds they manage"),
    ) -> List[FundResponse]:
        """Return investment funds with optional filtering and sorting; includes computed value and profit."""
        bearer = raw_request.state.raw_token
        manager_id: Optional[int] = None
        if my_funds and any(r in token.roles for r in ("SUPERVISOR", "ADMIN")):
            manager_id = token.id
        funds = await service.list_funds(name_contains=name_contains, manager_id=manager_id, sort_by=sort_by, sort_order=sort_order)

        async def _enrich(fund) -> FundResponse:
            try:
                vrednost = await valuation.get_cached_or_compute_vrednost(fund.id, fund, bearer)
                profit = await valuation.compute_profit(fund, vrednost)
            except Exception:
                vrednost = fund.likvidna_sredstva
                profit = Decimal("0")
            return FundResponse.model_validate(fund).model_copy(update={"vrednost_fonda": vrednost, "profit": profit})

        return list(await asyncio.gather(*[_enrich(f) for f in funds]))

    async def create_fund(self, request: CreateFundRequest, service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)], _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request) -> FundResponse:
        """Create a new investment fund (supervisor only); opens a banking account automatically."""
        bearer = raw_request.state.raw_token
        fund = await service.create_fund(request, bearer)
        return FundResponse.model_validate(fund)

    async def get_fund_detail(
        self,
        fund_id: int,
        service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)],
        valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)],
        order: Annotated[OrderClient, Depends(get_order_client)],
        employee: Annotated[EmployeeClient, Depends(get_employee_client)],
        token: Annotated[TokenData, Depends(require_authenticated)],
        raw_request: Request,
    ) -> FundDetailResponse:
        """Return fund details including computed vrednost_fonda, profit, securities, and manager name."""
        fund = await service.get_fund(fund_id)
        bearer = raw_request.state.raw_token
        vrednost_fonda = await valuation.get_cached_or_compute_vrednost(fund_id, fund, bearer)
        profit = await valuation.compute_profit(fund, vrednost_fonda)
        holdings = await valuation.get_fund_holdings(fund_id, bearer)
        securities = [_holding_to_security(h) for h in holdings]
        menadzer_ime: Optional[str] = None
        menadzer_prezime: Optional[str] = None
        try:
            mgr = await employee.get_employee(fund.menadzer_id, bearer)
            menadzer_ime = mgr.get("firstName") or mgr.get("ime") or mgr.get("first_name")
            menadzer_prezime = mgr.get("lastName") or mgr.get("prezime") or mgr.get("last_name")
        except Exception as exc:
            logger.warning("Could not fetch manager %s for fund %s: %s", fund.menadzer_id, fund_id, exc)
        return FundDetailResponse(
            id=fund.id, naziv=fund.naziv, opis=fund.opis, minimalni_ulog=fund.minimalni_ulog,
            menadzer_id=fund.menadzer_id, menadzer_ime=menadzer_ime, menadzer_prezime=menadzer_prezime,
            likvidna_sredstva=fund.likvidna_sredstva, account_id=fund.account_id,
            datum_kreiranja=fund.datum_kreiranja, vrednost_fonda=vrednost_fonda, profit=profit, securities=securities,
        )

    async def update_fund(self, fund_id: int, request: UpdateFundRequest, service: Annotated[InvestmentFundService, Depends(get_investment_fund_service)], _token: Annotated[TokenData, Depends(require_supervisor)], raw_request: Request) -> FundResponse:
        """Partially update a fund's description, minimum investment, or manager (supervisor only)."""
        bearer = raw_request.state.raw_token
        fund = await service.update_fund(fund_id, request, bearer)
        return FundResponse.model_validate(fund)


def _holding_to_security(h: dict) -> SecurityItem:
    """Map an order-service portfolio holding dict to a SecurityItem schema."""
    ticker = h.get("ticker") or h.get("symbol") or str(h.get("listingId", ""))
    price = Decimal(str(h.get("currentPrice", 0)))
    change_raw = h.get("change") or h.get("priceChange")
    change = Decimal(str(change_raw)) if change_raw is not None else None
    volume_raw = h.get("volume")
    volume = int(volume_raw) if volume_raw is not None else None
    margin_raw = h.get("initialMarginCost") or h.get("initialMargin")
    initial_margin_cost = Decimal(str(margin_raw)) if margin_raw is not None else None
    acquisition_date = h.get("acquisitionDate")
    quantity = int(h.get("quantity", 0))
    return SecurityItem(ticker=ticker, price=price, change=change, volume=volume, initial_margin_cost=initial_margin_cost, acquisition_date=acquisition_date, quantity=quantity)
