"""Router exposing client fund transaction read endpoints."""

from typing import Annotated, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query

from project.dependencies import get_client_fund_transaction_repository, require_authenticated, require_client_or_supervisor
from project.middleware.token_data import TokenData
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.schemas.transaction_response import TransactionResponse


class TransactionRouter:
    """Registers /transactions read routes and exposes them via the router property."""

    def __init__(self) -> None:
        """Create the APIRouter and register all transaction route handlers."""
        self._router = APIRouter(prefix="/transactions", tags=["transactions"])
        self._router.get("/", response_model=List[TransactionResponse])(self.list_transactions)
        self._router.get("/{transaction_id}", response_model=TransactionResponse)(self.get_transaction)

    @property
    def router(self) -> APIRouter:
        """Return the underlying APIRouter instance."""
        return self._router

    async def list_transactions(self, token: Annotated[TokenData, Depends(require_client_or_supervisor)], tx_repo: Annotated[ClientFundTransactionRepository, Depends(get_client_fund_transaction_repository)], fund_id: Optional[int] = Query(None, description="Filter by fund ID (supervisor/admin only)")) -> List[TransactionResponse]:
        """Return transactions; clients see only their own, supervisors/admins can filter by fund_id."""
        is_privileged = any(r in token.roles for r in ("SUPERVISOR", "ADMIN"))
        if is_privileged and fund_id is not None:
            txs = await tx_repo.find_by_fund_id(fund_id)
        elif is_privileged:
            txs = await tx_repo.find_all()
        else:
            txs = await tx_repo.find_by_klijent_id(token.id)
        return [TransactionResponse(id=t.id, klijent_id=t.klijent_id, fund_id=t.fund_id, iznos=t.iznos, status=t.status, is_inflow=t.is_inflow, timestamp=t.timestamp) for t in txs]

    async def get_transaction(self, transaction_id: int, token: Annotated[TokenData, Depends(require_authenticated)], tx_repo: Annotated[ClientFundTransactionRepository, Depends(get_client_fund_transaction_repository)]) -> TransactionResponse:
        """Return a single transaction; clients may only access their own."""
        tx = await tx_repo.find_by_id(transaction_id)
        if tx is None:
            raise HTTPException(status_code=404, detail="Transaction not found")
        is_privileged = any(r in token.roles for r in ("SUPERVISOR", "ADMIN"))
        if not is_privileged and tx.klijent_id != token.id:
            raise HTTPException(status_code=403, detail="Access denied")
        return TransactionResponse(id=tx.id, klijent_id=tx.klijent_id, fund_id=tx.fund_id, iznos=tx.iznos, status=tx.status, is_inflow=tx.is_inflow, timestamp=tx.timestamp)
