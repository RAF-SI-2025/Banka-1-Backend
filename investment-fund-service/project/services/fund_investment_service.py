"""Service handling client investments (deposits) into a fund."""

import logging
from decimal import Decimal
from uuid import uuid4

from fastapi import HTTPException

from project.clients.banking_client import BankingClient
from project.constants import BANK_KLIJENT_ID
from project.enums.transaction_status import TransactionStatus
from project.models.client_fund_transaction import ClientFundTransaction
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.invest_request import InvestRequest

logger = logging.getLogger(__name__)


class FundInvestmentService:
    """Executes the deposit flow with idempotency and position tracking."""

    def __init__(self, fund_repo: InvestmentFundRepository, tx_repo: ClientFundTransactionRepository, position_repo: ClientFundPositionRepository, banking_client: BankingClient) -> None:
        """Initialise with fund/transaction/position repositories and the banking client."""
        self._fund_repo = fund_repo
        self._tx_repo = tx_repo
        self._position_repo = position_repo
        self._banking = banking_client

    async def invest(self, fund_id: int, klijent_id: int, request: InvestRequest, bearer_token: str, is_supervisor: bool = False) -> ClientFundTransaction:
        """Deposit request.iznos from source_account_id into the fund; returns the transaction record."""
        fund = await self._fund_repo.find_by_id(fund_id)
        if not fund:
            raise HTTPException(status_code=404, detail=f"Fund {fund_id} not found")
        if request.iznos < fund.minimalni_ulog:
            raise HTTPException(status_code=400, detail=f"Amount {request.iznos} is below minimum investment {fund.minimalni_ulog}")
        idempotency_key = request.idempotency_key or str(uuid4())
        existing = await self._tx_repo.find_by_idempotency_key(idempotency_key)
        if existing:
            return existing

        source_details = await self._banking.get_account_details(request.source_account_id)
        position_owner_id = self._resolve_position_owner(klijent_id, source_details, is_supervisor)

        tx = ClientFundTransaction(klijent_id=position_owner_id, fund_id=fund_id, iznos=request.iznos, status=TransactionStatus.PENDING, is_inflow=True, source_account_id=request.source_account_id, idempotency_key=idempotency_key)
        tx = await self._tx_repo.save(tx)
        try:
            fund_details = await self._banking.get_account_details(fund.account_id)
            source_number = source_details.get("accountNumber") or source_details.get("account_number")
            fund_number = fund_details.get("accountNumber") or fund_details.get("account_number")
            await self._banking.transfer(source_number, fund_number, request.iznos, Decimal("0"), position_owner_id)
            tx.status = TransactionStatus.COMPLETED
            await self._fund_repo.update_likvidna_sredstva(fund_id, request.iznos)
            await self._position_repo.upsert(position_owner_id, fund_id, request.iznos)
        except Exception as exc:
            logger.error("invest failed for fund %s klijent %s: %s", fund_id, position_owner_id, exc)
            tx.status = TransactionStatus.FAILED
            await self._tx_repo.save(tx)
            raise HTTPException(status_code=502, detail=f"Banking transfer failed: {exc}") from exc
        await self._tx_repo.save(tx)
        return tx

    def _resolve_position_owner(self, klijent_id: int, account_details: dict, is_supervisor: bool) -> int:
        """Determine which klijent_id should own the position based on the source account."""
        account_client_id = account_details.get("clientId") or account_details.get("client_id")
        if account_client_id is None:
            return klijent_id
        account_client_id = int(account_client_id)
        if account_client_id == klijent_id:
            return klijent_id
        if is_supervisor:
            return BANK_KLIJENT_ID
        raise HTTPException(status_code=403, detail="Account does not belong to the authenticated user")
