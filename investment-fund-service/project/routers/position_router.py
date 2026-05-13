"""Router exposing client fund position read endpoints."""

from decimal import Decimal
from typing import Annotated, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request

from project.dependencies import get_client_fund_position_repository, get_fund_valuation_service, get_investment_fund_repository, require_authenticated, require_client_or_supervisor
from project.middleware.token_data import TokenData
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.position_response import PositionResponse
from project.services.fund_valuation_service import FundValuationService


class PositionRouter:
    """Registers /positions read routes and exposes them via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register all position route handlers."""
        self._router = APIRouter(prefix="/positions", tags=["positions"])
        self._router.get("/", response_model=List[PositionResponse])(self.list_positions)
        self._router.get("/{position_id}", response_model=PositionResponse)(self.get_position)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def list_positions(self, token: Annotated[TokenData, Depends(require_client_or_supervisor)], raw_request: Request, position_repo: Annotated[ClientFundPositionRepository, Depends(get_client_fund_position_repository)], fund_repo: Annotated[InvestmentFundRepository, Depends(get_investment_fund_repository)], valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)], fund_id: Optional[int] = Query(None, description="Filter by fund ID (supervisor/admin only)")) -> List[PositionResponse]:
        """Return positions; clients see only their own, supervisors/admins can filter by fund_id."""
        bearer = raw_request.state.raw_token
        is_privileged = any(r in token.roles for r in ("SUPERVISOR", "ADMIN"))
        if is_privileged and fund_id is not None:
            positions = await position_repo.find_by_fund_id(fund_id)
        elif is_privileged:
            positions = await position_repo.find_all()
        else:
            positions = await position_repo.find_by_klijent_id(token.id)
        return [await self._build_response(p, fund_repo, valuation, bearer) for p in positions]

    async def get_position(self, position_id: int, token: Annotated[TokenData, Depends(require_authenticated)], raw_request: Request, position_repo: Annotated[ClientFundPositionRepository, Depends(get_client_fund_position_repository)], fund_repo: Annotated[InvestmentFundRepository, Depends(get_investment_fund_repository)], valuation: Annotated[FundValuationService, Depends(get_fund_valuation_service)]) -> PositionResponse:
        """Return a single position; clients may only access their own."""
        bearer = raw_request.state.raw_token
        position = await position_repo.find_by_id(position_id)
        if position is None:
            raise HTTPException(status_code=404, detail="Position not found")
        is_privileged = any(r in token.roles for r in ("SUPERVISOR", "ADMIN"))
        if not is_privileged and position.klijent_id != token.id:
            raise HTTPException(status_code=403, detail="Access denied")
        return await self._build_response(position, fund_repo, valuation, bearer)

    async def _build_response(self, position, fund_repo: InvestmentFundRepository, valuation: FundValuationService, bearer: str) -> PositionResponse:
        """Compute derived fields and return a PositionResponse for the given position."""
        fund = await fund_repo.find_by_id(position.fund_id)
        if fund is None:
            return PositionResponse(id=position.id, klijent_id=position.klijent_id, fund_id=position.fund_id, ukupan_ulozeni_iznos=position.ukupan_ulozeni_iznos, datum_poslednje_promene=position.datum_poslednje_promene, procenat_fonda=Decimal("0"), trenutna_vrednost_pozicije=Decimal("0"), ostvareni_profit=-position.ukupan_ulozeni_iznos)
        vrednost_fonda = await valuation.get_cached_or_compute_vrednost(position.fund_id, fund, bearer)
        procenat = await valuation.compute_procenat_fonda(position.klijent_id, position.fund_id)
        trenutna_vrednost = procenat * vrednost_fonda
        ostvareni_profit = trenutna_vrednost - position.ukupan_ulozeni_iznos
        return PositionResponse(id=position.id, klijent_id=position.klijent_id, fund_id=position.fund_id, fund_naziv=fund.naziv, fund_opis=fund.opis, ukupan_ulozeni_iznos=position.ukupan_ulozeni_iznos, datum_poslednje_promene=position.datum_poslednje_promene, procenat_fonda=procenat, trenutna_vrednost_pozicije=trenutna_vrednost, ostvareni_profit=ostvareni_profit, vrednost_fonda=vrednost_fonda)
