"""Service handling client redemptions (withdrawals) from a fund."""

from decimal import Decimal

from fastapi import HTTPException

from project.clients.banking_client import BankingClient
from project.enums.transaction_status import TransactionStatus
from project.models.client_fund_transaction import ClientFundTransaction
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.redeem_request import RedeemRequest
from project.services.fund_liquidation_service import FundLiquidationService


class FundRedemptionService:
    """Executes the withdrawal flow, routing to direct transfer or SAGA liquidation as needed."""

    def __init__(self, fund_repo: InvestmentFundRepository, tx_repo: ClientFundTransactionRepository, position_repo: ClientFundPositionRepository, banking_client: BankingClient, liquidation_service: FundLiquidationService) -> None:
        """Initialise with fund/transaction/position repositories, banking client, and liquidation service."""
        self._fund_repo = fund_repo
        self._tx_repo = tx_repo
        self._position_repo = position_repo
        self._banking = banking_client
        self._liquidation = liquidation_service

    async def redeem(self, fund_id: int, klijent_id: int, request: RedeemRequest, bearer_token: str) -> tuple[ClientFundTransaction, bool]:
        """Withdraw request.iznos from the fund; returns (transaction, liquidation_started)."""
        fund = await self._fund_repo.find_by_id(fund_id)
        if not fund:
            raise HTTPException(status_code=404, detail=f"Fund {fund_id} not found")
        position = await self._position_repo.find_by_klijent_and_fund(klijent_id, fund_id)
        if not position:
            raise HTTPException(status_code=400, detail="Client has no position in this fund")
        if position.ukupan_ulozeni_iznos < request.iznos:
            raise HTTPException(status_code=400, detail=f"Insufficient position: {position.ukupan_ulozeni_iznos} available")
        tx = ClientFundTransaction(klijent_id=klijent_id, fund_id=fund_id, iznos=request.iznos, status=TransactionStatus.PENDING, is_inflow=False, source_account_id=request.destination_account_id)
        tx = await self._tx_repo.save(tx)
        liquidation_started = False
        if request.iznos <= fund.likvidna_sredstva:
            try:
                fund_details = await self._banking.get_account_details(fund.account_id)
                dest_details = await self._banking.get_account_details(request.destination_account_id)
                fund_number = fund_details.get("accountNumber") or fund_details.get("account_number")
                dest_number = dest_details.get("accountNumber") or dest_details.get("account_number")
                await self._banking.transfer(fund_number, dest_number, request.iznos, Decimal("0"), klijent_id)
                tx.status = TransactionStatus.COMPLETED
                await self._fund_repo.update_likvidna_sredstva(fund_id, -request.iznos)
                await self._position_repo.upsert(klijent_id, fund_id, -request.iznos)
            except Exception as exc:
                tx.status = TransactionStatus.FAILED
                await self._tx_repo.save(tx)
                raise HTTPException(status_code=502, detail=f"Banking transfer failed: {exc}") from exc
        else:
            await self._liquidation.start_liquidation(fund, request.iznos, klijent_id, request.destination_account_id, tx.id, bearer_token)
            liquidation_started = True
        await self._tx_repo.save(tx)
        return tx, liquidation_started
