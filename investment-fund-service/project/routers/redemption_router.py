"""Router class exposing the fund redemption (withdrawal) endpoint."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request

from project.dependencies import get_fund_redemption_service, require_client_or_supervisor
from project.middleware.token_data import TokenData
from project.schemas.redeem_request import RedeemRequest
from project.schemas.redeem_response import RedeemResponse
from project.services.fund_redemption_service import FundRedemptionService


class RedemptionRouter:
    """Registers POST /funds/{fund_id}/redeem and exposes it via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register the redeem route handler."""
        self._router = APIRouter(prefix="/funds", tags=["redemption"])
        self._router.post("/{fund_id}/redeem", response_model=RedeemResponse)(self.redeem)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def redeem(self, fund_id: int, request: RedeemRequest, service: Annotated[FundRedemptionService, Depends(get_fund_redemption_service)], token: Annotated[TokenData, Depends(require_client_or_supervisor)], raw_request: Request) -> RedeemResponse:
        """Withdraw the requested amount from the fund to a client account; may trigger SAGA."""
        bearer = raw_request.state.raw_token
        tx, liquidation_started = await service.redeem(fund_id, token.id, request, bearer)
        return RedeemResponse(transaction_id=tx.id, status=tx.status, iznos=tx.iznos, fund_id=tx.fund_id, klijent_id=tx.klijent_id, liquidation_started=liquidation_started, timestamp=tx.timestamp)
