"""Router class exposing the fund investment (deposit) endpoint."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request

from project.dependencies import get_fund_investment_service, require_client_or_supervisor
from project.middleware.token_data import TokenData
from project.schemas.invest_request import InvestRequest
from project.schemas.invest_response import InvestResponse
from project.services.fund_investment_service import FundInvestmentService


class InvestmentRouter:
    """Registers POST /funds/{fund_id}/invest and exposes it via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register the invest route handler."""
        self._router = APIRouter(prefix="/funds", tags=["investment"])
        self._router.post("/{fund_id}/invest", response_model=InvestResponse, status_code=201)(self.invest)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def invest(self, fund_id: int, request: InvestRequest, service: Annotated[FundInvestmentService, Depends(get_fund_investment_service)], token: Annotated[TokenData, Depends(require_client_or_supervisor)], raw_request: Request) -> InvestResponse:
        """Deposit the requested amount from a client or bank account into the fund."""
        bearer = raw_request.state.raw_token
        is_supervisor = any(r in token.roles for r in ("SUPERVISOR", "ADMIN"))
        tx = await service.invest(fund_id, token.id, request, bearer, is_supervisor=is_supervisor)
        return InvestResponse(transaction_id=tx.id, status=tx.status, iznos=tx.iznos, fund_id=tx.fund_id, klijent_id=tx.klijent_id, timestamp=tx.timestamp)
