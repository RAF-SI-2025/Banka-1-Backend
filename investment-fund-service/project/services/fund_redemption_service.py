"""Service handling client redemptions (withdrawals) from a fund."""

import logging
from decimal import Decimal

from fastapi import HTTPException

from project.clients.banking_client import BankingClient
from project.constants import BANK_KLIJENT_ID
from project.enums.transaction_status import TransactionStatus
from project.models.client_fund_transaction import ClientFundTransaction
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.redeem_request import RedeemRequest
from project.services.fund_liquidation_service import FundLiquidationService
from project.services.fund_valuation_service import FundValuationService

logger = logging.getLogger(__name__)


class FundRedemptionService:
    """Executes the withdrawal flow, routing to direct transfer or SAGA liquidation as needed."""

    def __init__(self, fund_repo: InvestmentFundRepository, tx_repo: ClientFundTransactionRepository, position_repo: ClientFundPositionRepository, banking_client: BankingClient, liquidation_service: FundLiquidationService, valuation_service: FundValuationService) -> None:
        """Initialise with repositories, banking/liquidation/valuation services."""
        self._fund_repo = fund_repo
        self._tx_repo = tx_repo
        self._position_repo = position_repo
        self._banking = banking_client
        self._liquidation = liquidation_service
        self._valuation = valuation_service

    async def redeem(self, fund_id: int, klijent_id: int, request: RedeemRequest, bearer_token: str, is_supervisor: bool = False, commission_rate: float = 0.0) -> tuple[ClientFundTransaction, bool]:
        """Withdraw from the fund; returns (transaction, liquidation_started)."""
        fund = await self._fund_repo.find_by_id(fund_id)
        if not fund:
            raise HTTPException(status_code=404, detail=f"Fund {fund_id} not found")

        if request.idempotency_key:
            existing = await self._tx_repo.find_by_idempotency_key(request.idempotency_key)
            if existing:
                return existing, False

        dest_details = await self._banking.get_account_details(request.destination_account_id)
        position_owner_id = self._resolve_position_owner(klijent_id, dest_details, is_supervisor)

        position = await self._position_repo.find_by_klijent_and_fund(position_owner_id, fund_id)
        if not position:
            raise HTTPException(status_code=400, detail="Client has no position in this fund")

        vrednost_fonda = await self._valuation.get_cached_or_compute_vrednost(fund_id, fund, bearer_token)
        procenat = await self._valuation.compute_procenat_fonda(position_owner_id, fund_id)
        current_value = procenat * vrednost_fonda

        if request.withdraw_all:
            iznos = current_value
            if iznos <= Decimal("0"):
                raise HTTPException(status_code=400, detail="No position value to withdraw")
        else:
            iznos = request.iznos  # type: ignore[assignment]
            if current_value < iznos:
                raise HTTPException(status_code=400, detail=f"Insufficient position value: {current_value} available")

        commission_decimal = Decimal("0") if is_supervisor else Decimal(str(commission_rate)) * iznos

        tx = ClientFundTransaction(klijent_id=position_owner_id, fund_id=fund_id, iznos=iznos, status=TransactionStatus.PENDING, is_inflow=False, source_account_id=request.destination_account_id, commission_rate=Decimal("0") if is_supervisor else Decimal(str(commission_rate)), idempotency_key=request.idempotency_key)
        tx = await self._tx_repo.save(tx)
        liquidation_started = False
        if iznos <= fund.likvidna_sredstva:
            try:
                fund_details = await self._banking.get_account_details(fund.account_id)
                fund_number = fund_details.get("accountNumber") or fund_details.get("account_number")
                dest_number = dest_details.get("accountNumber") or dest_details.get("account_number")
                await self._banking.transfer(fund_number, dest_number, iznos, commission_decimal, position_owner_id)
                tx.status = TransactionStatus.COMPLETED
                await self._fund_repo.update_likvidna_sredstva(fund_id, -iznos)
                await self._position_repo.upsert(position_owner_id, fund_id, -iznos)
            except Exception as exc:
                logger.error("redeem transfer failed for fund %s klijent %s: %s", fund_id, position_owner_id, exc)
                tx.status = TransactionStatus.FAILED
                await self._tx_repo.save(tx)
                raise HTTPException(status_code=502, detail=f"Banking transfer failed: {exc}") from exc
        else:
            await self._liquidation.start_liquidation(fund, iznos, position_owner_id, request.destination_account_id, tx.id, bearer_token)
            liquidation_started = True
        await self._tx_repo.save(tx)
        return tx, liquidation_started

    def _resolve_position_owner(self, klijent_id: int, account_details: dict, is_supervisor: bool) -> int:
        """Determine which klijent_id owns the position based on the destination account."""
        account_client_id = account_details.get("clientId") or account_details.get("client_id")
        if account_client_id is None:
            return klijent_id
        account_client_id = int(account_client_id)
        if account_client_id == klijent_id:
            return klijent_id
        if is_supervisor:
            return BANK_KLIJENT_ID
        raise HTTPException(status_code=403, detail="Account does not belong to the authenticated user")
